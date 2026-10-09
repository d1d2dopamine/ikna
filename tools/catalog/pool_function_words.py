#!/usr/bin/env python3
"""Export the exact function-rank prefix of a saved admitted pool; never selects.

Count each primary-origin context key once, preserving its first-seen text and
rowid order exactly as stage_candidates/build_ranks. Alternate origins and
translations do not multiply frequency evidence. Outputs are input-bound data,
not a linguistic POS dictionary or publication approval.
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import platform
import re
import shutil
import sqlite3
import tempfile
import zipfile

from build_catalogue_v2 import build_ranks, primary_origin
from everyday_pool import DEFAULT_REGISTRY
from ingest.model import Candidate
from ingest.registry import SourceRegistry
from segmentation import prepare

HERE = Path(__file__).resolve().parent
POOL_MEMBER = "pool/everyday-candidates.jsonl.gz"
REPORT_MEMBER = "reports/EVERYDAY-POOL.json"
CONTEXT_SCHEMA = """
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
PRAGMA temp_store=FILE;
CREATE TABLE context (
 collection TEXT NOT NULL, lang TEXT NOT NULL, source_family TEXT NOT NULL,
 context_ref TEXT NOT NULL, context TEXT NOT NULL,
 PRIMARY KEY(collection,lang,source_family,context_ref)
);
CREATE INDEX context_language ON context(collection,lang);
"""


def sha_file(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def encode(value: dict) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def stage_contexts(rows, db: sqlite3.Connection, report: dict, progress=False) -> dict:
    """The same context INSERT OR IGNORE as the builder, without candidate payloads."""
    db.executescript(CONTEXT_SCHEMA)
    policy = SourceRegistry.load(str(DEFAULT_REGISTRY)).get("tatoeba")
    policy.require_publication_ready()
    pairs, origins, contributors = Counter(), Counter(), Counter()
    row_count = 0
    ref = re.compile(r"tatoeba:[1-9][0-9]*")
    for value in rows:
        record = Candidate.from_dict(value)
        if (record.collection != "everyday" or record.lang not in report["learn"] or
                record.meaning_lang not in report["meanings"] or record.lang == record.meaning_lang):
            raise ValueError("candidate outside the pinned scope")
        for origin in record.origins:
            if origin.source_family != "tatoeba" or origin.source_version != report["sourceVersion"]:
                raise ValueError("origin differs from the admitted source/version")
            if not ref.fullmatch(origin.context_ref) or not ref.fullmatch(origin.meaning_ref):
                raise ValueError("invalid source sentence reference")
            policy.validate_origin_for_publication(origin.to_dict())
            origins[origin.source_family] += 1
            for key in ("contextContributor", "meaningContributor"):
                if origin.attribution.get(key):
                    contributors[key] += 1
        origin = primary_origin(record)
        db.execute("INSERT OR IGNORE INTO context VALUES(?,?,?,?,?)", (
            record.collection, record.lang, origin["sourceFamily"],
            origin["contextRef"], record.context))
        pairs[record.lang + "->" + record.meaning_lang] += 1
        row_count += 1
        if row_count % 25000 == 0:
            db.commit()
        if progress and row_count % 500000 == 0:
            print(f"validated {row_count:,} candidate rows", flush=True)
    db.commit()
    contexts = dict(db.execute("SELECT lang,COUNT(*) FROM context GROUP BY lang"))
    return {"candidateRows": row_count, "uniquePrimaryContexts": sum(contexts.values()),
            "contextsByLanguage": contexts, "candidatesByPair": dict(sorted(pairs.items())),
            "originOccurrencesByFamily": dict(origins), "namedOriginOccurrences": dict(contributors)}


def function_prefix(db: sqlite3.Connection, languages: list[str], top: int) -> dict:
    result = {}
    for lang in languages:
        print("ranking " + lang, flush=True)
        ranks = build_ranks(db, "everyday", lang)
        prefix = [{"form": form, "rank": rank} for form, rank in ranks.items() if rank <= top]
        result[lang] = {"rankedForms": len(ranks), "functionForms": prefix}
    return result


def run(archive: Path, archive_sha: str, report_sha: str, output: Path) -> dict:
    archive, output = archive.resolve(), output.resolve()
    if output.exists():
        raise ValueError("output must be a new directory")
    if output == archive or archive.is_relative_to(output):
        raise ValueError("output cannot contain the input archive")
    if sha_file(archive) != archive_sha.lower():
        raise ValueError("archive SHA-256 mismatch")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="pool-prefix-", dir=output.parent) as td:
        work = Path(td)
        with zipfile.ZipFile(archive) as zipped:
            members = zipped.namelist()
            if len(members) != len(set(members)):
                raise ValueError("duplicate archive member")
            raw_report = zipped.read(REPORT_MEMBER)
            if hashlib.sha256(raw_report).hexdigest() != report_sha.lower():
                raise ValueError("pool report SHA-256 mismatch")
            report = json.loads(raw_report)
            if (report.get("part") != 7 or report.get("status") != "admitted-source-pool-evidence" or
                    report.get("sourceAdmissionPassed") is not True or report.get("completeInputScan") is not True or
                    report.get("publicationSafe") is not False):
                raise ValueError("not a pinned admitted nonpublishing pool report")
            if report.get("corpusCoverage") != "provided-input-files-only":
                raise ValueError("unexpected coverage contract")
            if sha_file(DEFAULT_REGISTRY) != report["registrySha256"]:
                raise ValueError("source registry changed")
            for name, digest in report["pipelineSha256"].items():
                path = (HERE / name).resolve()
                if not path.is_relative_to(HERE) or sha_file(path) != digest:
                    raise ValueError("pool pipeline changed: " + name)
            if platform.python_version() != report["environment"]["python"]:
                raise ValueError("Python differs from the pinned rank environment")
            segmentation = prepare(report["learn"])
            if segmentation != report["environment"]["segmentation"]:
                raise ValueError("segmentation differs from the pinned rank environment")
            top = report["sieve"]["functionTop"]
            if not isinstance(top, int) or isinstance(top, bool) or top < 0:
                raise ValueError("invalid function-top")
            pool = work / "pool.jsonl.gz"
            with zipped.open(POOL_MEMBER) as source, pool.open("wb") as dest:
                shutil.copyfileobj(source, dest, 8 * 1024 * 1024)
            if sha_file(pool) != report["pool"]["sha256"] or pool.stat().st_size != report["pool"]["sizeBytes"]:
                raise ValueError("pool bytes differ from report")
            # Read every archive member through EOF, including small evidence.
            for name in members:
                if name not in (POOL_MEMBER, REPORT_MEMBER):
                    with zipped.open(name) as stream:
                        while stream.read(1024 * 1024):
                            pass
        logical = hashlib.sha256()
        logical_bytes = 0

        def rows():
            nonlocal logical_bytes
            with gzip.open(pool, "rb") as stream:
                for line in stream:
                    logical.update(line)
                    logical_bytes += len(line)
                    if line.strip():
                        yield json.loads(line)

        db = sqlite3.connect(work / "contexts.sqlite3")
        try:
            census = stage_contexts(rows(), db, report, progress=True)
            if (logical.hexdigest() != report["pool"]["logicalSha256"] or
                    logical_bytes != report["pool"]["logicalSizeBytes"]):
                raise ValueError("logical pool identity mismatch")
            if census["candidateRows"] != report["pool"]["uniqueCandidates"]:
                raise ValueError("candidate count mismatch")
            if census["uniquePrimaryContexts"] != report["staging"]["uniqueContexts"]:
                raise ValueError("context count mismatch")
            # Part 7 pairs.inputCandidates counts scoped rows BEFORE merging.
            # Its pool/staging counts refer to the retained deduplicated file.
            supplied_pairs = {p["lang"] + "->" + p["meaningLang"]: p["inputCandidates"] for p in report["pairs"]}
            retained_pairs = census["candidatesByPair"]
            if set(retained_pairs) != set(supplied_pairs):
                raise ValueError("pair scope mismatch")
            removed = {pair: supplied_pairs[pair] - retained_pairs[pair] for pair in supplied_pairs}
            if (any(n < 0 for n in removed.values()) or
                    sum(supplied_pairs.values()) != report["inputs"]["scopedCandidates"] or
                    sum(removed.values()) != report["pool"]["duplicateCandidatesMerged"]):
                raise ValueError("premerge/retained pair count mismatch")
            census["premergeCandidatesByPairFromReport"] = dict(sorted(supplied_pairs.items()))
            census["removedDuplicateCandidatesByPair"] = dict(sorted(removed.items()))
            prefixes = function_prefix(db, report["learn"], top)
        finally:
            db.close()
        result = {"reportVersion": 1, "status": "verified-admitted-pool-function-prefix",
            "publicationSafe": False, "semanticAccuracyCertified": False,
            "selectionReexecuted": False, "corpusAcquired": False,
            "input": {"archiveSha256": archive_sha.lower(), "archiveBytes": archive.stat().st_size,
                "poolReportSha256": report_sha.lower(), **report["pool"]},
            "sourceVersion": report["sourceVersion"], "registrySha256": report["registrySha256"],
            "poolPipelineSha256": report["pipelineSha256"],
            "exporterSha256": sha_file(Path(__file__)),
            "environment": {"python": platform.python_version(), "sqlite": sqlite3.sqlite_version,
                "segmentation": segmentation}, "functionTop": top, "census": census,
            "rankContract": "primary-origin context key once; first-seen text/rowid; lower() tokens; Counter.most_common first-seen ties",
            "candidateUniqueness": "inherited from exact hash-bound admitted report; not independently indexed again",
            "languages": prefixes}
        (work / "FUNCTION-WORDS.json").write_bytes(encode(result))
        # Only publish a compact completed result; temporary raw pool/index stay out.
        output.mkdir()
        shutil.copyfile(work / "FUNCTION-WORDS.json", output / "FUNCTION-WORDS.json")
        (output / "EVERYDAY-POOL.json").write_bytes(raw_report)
        return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--expect-archive-sha256", required=True)
    parser.add_argument("--expect-report-sha256", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    result = run(args.archive, args.expect_archive_sha256, args.expect_report_sha256, args.output_dir)
    print("Complete:", result["census"]["candidateRows"], "rows;", len(result["languages"]), "language prefixes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
