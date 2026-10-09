#!/usr/bin/env python3
"""Offline pinned corpus diagnostics, not source admission or catalogue assembly.

Measure whole WMT social/speech segments and MKQA query pairs in pl<->ko using
the original Everyday frequency space. Unknown vocabulary is reported separately,
never assigned an invented advanced rank. NTREX gets Knowledge shape checks only.
No registry, app files or original pool/selection is changed.
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
import platform
from pathlib import Path
import sqlite3
import tempfile

import catalogue_core as core
from catalogue_v2 import target_id
from ingest.model import Candidate, Origin, merge_candidates, write_jsonl
from segmentation import prepare, utf16_length
import selection_experiment as selection
from selection_policy import choose_contexts, select_target_ids


def sha(path: Path) -> str:
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def verify_files(root: Path, manifest: list[dict]) -> None:
    names = set()
    for entry in manifest:
        name = entry["file"]
        if Path(name).name != name or name in names or "error" in entry:
            raise ValueError("invalid or incomplete input manifest")
        names.add(name)
        path = root / name
        if path.stat().st_size != entry["sizeBytes"] or sha(path) != entry["sha256"]:
            raise ValueError("candidate bytes differ from pin: " + name)


def wmt_join(left: list[dict], right: list[dict]) -> tuple[list[tuple], dict]:
    def index(rows):
        result = {}
        for row in rows:
            key = (row["document_id"], row["segment_id"])
            if key in result or not isinstance(row["is_bad_source"], bool):
                raise ValueError("duplicate WMT anchor or invalid bad-source flag")
            result[key] = row
        return result
    a, b = index(left), index(right)
    if a.keys() != b.keys():
        raise ValueError("WMT language anchor sets differ")
    output, rejected = [], Counter()
    for key in sorted(a):
        x, y = a[key], b[key]
        if x["source"] != y["source"] or x["domain"] != y["domain"]:
            raise ValueError("WMT anchor does not identify the same source segment")
        if x["is_bad_source"] or y["is_bad_source"]:
            rejected["bad-source"] += 1
        elif x["domain"] not in ("social", "speech"):
            rejected["outside-everyday-domains"] += 1
        elif not x["target"].strip() or not y["target"].strip():
            rejected["empty-human-reference"] += 1
        else:
            output.append((f"{key[0]}:{key[1]}", x["target"], y["target"]))
    return output, {"sourceSegments": len(a), "retainedDomainSegments": len(output), "rejected": dict(rejected)}


def pair_candidates(rows, family: str, revision: str) -> list[Candidate]:
    result = []
    for anchor, pl, ko in rows:
        for lang, meaning, text, translation in (("pl", "ko", pl, ko), ("ko", "pl", ko, pl)):
            result.append(Candidate(collection="everyday", lang=lang, meaning_lang=meaning,
                                    context=text, meaning=translation,
                                    origins=[Origin(family, revision, f"{family}:{anchor}:{lang}",
                                                    f"{family}:{anchor}:{meaning}")]))
    return result


def allocate(targets: list[dict], lang: str) -> dict:
    result = {}
    for level in core.LEVELS:
        bucket = [t for t in targets if t["level"] == level]
        ordered, _ = select_target_ids(bucket, max(1, len(bucket)))
        used, kept, collisions = set(), [], 0
        for target in ordered:
            if len(kept) >= core.V2_MAX_DECK_TARGETS:
                break
            unused = [c for c in target["contexts"] if str(c.get("contextId") or c.get("candidateId")) not in used]
            contexts, _ = choose_contexts(unused, 3, .85, diverse=True, lang=lang)
            if not contexts:
                collisions += 1
                continue
            kept.append({"targetId": target["targetId"], "contexts": len(contexts)})
            used.update(str(c.get("contextId") or c.get("candidateId")) for c in contexts)
        result[level] = {"eligibleTargets": len(bucket), "selectedTargets": len(kept),
                         "contextCollisionTargetsRejected": collisions, "included": len(kept) >= 40,
                         "below1000": len(kept) < 1000, "selectedIds": [t["targetId"] for t in kept],
                         "retainedContexts": sum(t["contexts"] for t in kept)}
    return result


def measure(records: list[Candidate], ranks: dict, scratch: Path, label: str) -> dict:
    inp, db_path = scratch / (label + ".jsonl.gz"), scratch / (label + ".db")
    merged = merge_candidates(records)
    write_jsonl(str(inp), merged)
    staging = selection.stage([str(inp)], str(db_path))
    db = sqlite3.connect(db_path)
    try:
        db.execute("PRAGMA journal_mode=OFF")
        db.execute("PRAGMA synchronous=OFF")
        output = {}
        for lang, meaning in (("pl", "ko"), ("ko", "pl")):
            selection.build_context_analysis(db, "everyday", lang, ranks[lang], 60, canonical_unique=True)
            unknown = set()
            for (text,) in db.execute("SELECT context FROM context WHERE lang=?", (lang,)):
                for surface in core.words(text, lang):
                    if surface.lower() not in ranks[lang] and len(surface) >= core.minimum_phrase(lang):
                        unknown.add(target_id(lang, surface))
            # phrase_choices already excludes absent ranks. Do not stage an
            # artificial rank for unknown forms or duplicate the production gate.
            targets, stats = selection.pair_evidence(db, "everyday", lang, meaning, None, 12, diverse=True)
            output[lang + "->" + meaning] = {"levels": allocate(targets, lang), "sieve": dict(stats),
                                            "outOfBaselineWrittenFormsBeforeSieve": len(unknown),
                                            "originPreservingMerge": {"inputCandidates": len(records),
                                                                     "uniqueCandidates": len(merged),
                                                                     "originOccurrences": sum(len(r.origins) for r in merged),
                                                                     "stageDuplicateCandidates": staging["duplicateCandidates"]}}
        return output
    finally:
        db.close()


def run(primary: Path, manifest_path: Path, revisions_path: Path, ranks_path: Path,
        baseline_path: Path, expected_path: Path) -> dict:
    manifest = json.loads(manifest_path.read_text())
    revisions = json.loads(revisions_path.read_text())
    verify_files(primary, manifest)
    with gzip.open(ranks_path, "rt") as f:
        passport = json.load(f)
    with gzip.open(baseline_path, "rt") as f:
        baseline = [Candidate.from_dict(json.loads(line)) for line in f if line.strip()]
    with gzip.open(expected_path, "rt") as f:
        expected = json.load(f)
    admitted = expected["admittedInput"]
    if (passport.get("publicationSafe") is not False or
            passport["poolSha256"] != admitted["poolSha256"] or
            passport["poolLogicalSha256"] != admitted["poolLogicalSha256"] or
            passport["sourceVersion"] != admitted["sourceVersion"] or
            set(passport["ranks"]) != {"pl", "ko"}):
        raise ValueError("baseline ranks differ from the accepted input")
    prepare(["pl", "ko"])
    def jsonl(name):
        return [json.loads(line) for line in (primary / name).read_text().splitlines() if line.strip()]
    pl_rows, ko_rows = jsonl("wmt24pp--en-pl_PL.jsonl"), jsonl("wmt24pp--en-ko_KR.jsonl")
    wmt_rows, wmt_stats = wmt_join(pl_rows, ko_rows)
    language_files = sorted(entry["file"] for entry in manifest
                            if entry["file"].startswith("wmt24pp--en-") and entry["file"].endswith(".jsonl"))
    if len(language_files) != 10:
        raise ValueError("expected ten pinned WMT translations plus their shared English source")
    for name in language_files:
        rows = jsonl(name)
        if any(row["lp"] != name.removeprefix("wmt24pp--").removesuffix(".jsonl") for row in rows):
            raise ValueError("WMT locale/file mismatch")
        wmt_join(pl_rows, rows)
    wmt_stats["sharedAnchorsVerifiedLanguages"] = 11
    wmt = pair_candidates(wmt_rows, "wmt24pp", revisions["wmt24pp"])
    with gzip.open(primary / "mkqa--dataset--mkqa.jsonl.gz", "rt") as f:
        mkqa_rows, seen = [], set()
        for line in f:
            row = json.loads(line)
            anchor = str(row["example_id"])
            if anchor in seen:
                raise ValueError("duplicate MKQA identity")
            seen.add(anchor)
            languages = ["zh_cn" if l == "zh" else l for l in core.LEARNABLE]
            if not all(isinstance(row["queries"].get(l), str) and row["queries"][l].strip() for l in languages):
                raise ValueError("MKQA lacks a supported query")
            mkqa_rows.append((anchor, row["queries"]["pl"], row["queries"]["ko"]))
    mkqa = pair_candidates(mkqa_rows, "mkqa", revisions["mkqa"])
    # This diagnostic deliberately does not claim a semantic/Everyday classifier.
    # All query pairs form an optimistic ceiling before a domain acceptance gate.
    docs = (primary / "ntrex--DOCUMENT_IDS.tsv").read_text().splitlines()
    npl = (primary / "ntrex--NTREX-128--newstest2019-ref.pol.txt").read_text().splitlines()
    nko = (primary / "ntrex--NTREX-128--newstest2019-ref.kor.txt").read_text().splitlines()
    nen = (primary / "ntrex--NTREX-128--newstest2019-src.eng.txt").read_text().splitlines()
    if not len(docs) == len(npl) == len(nko) == len(nen) or any(not s.strip() for s in docs + npl + nko + nen):
        raise ValueError("NTREX line/document alignment is incomplete")
    nt_shape = {}
    for lang, texts, meanings in (("pl", npl, nko), ("ko", nko, npl)):
        counts = Counter()
        for text, meaning in zip(texts, meanings):
            if not core.MIN_SENTENCE <= utf16_length(text) <= core.MAX_SENTENCE:
                counts["sentenceLengthRejected"] += 1
            elif utf16_length(meaning) > core.MAX_TRANSLATION:
                counts["translationLengthRejected"] += 1
            else:
                counts["lengthEligiblePairs"] += 1
        nt_shape[lang] = dict(counts)
    with tempfile.TemporaryDirectory(prefix="ikna-replenishment-", dir=ranks_path.resolve().parent) as td:
        scratch = Path(td)
        trials = {"baseline": baseline, "wmt24pp-only": wmt, "baseline+wmt24pp": baseline + wmt,
                  "mkqa-all-queries-only": mkqa, "baseline+mkqa-all-queries": baseline + mkqa,
                  "baseline+wmt24pp+mkqa-all-queries": baseline + wmt + mkqa}
        measurements = {label: measure(records, passport["ranks"], scratch, label) for label, records in trials.items()}
    for row in expected["decks"]:
        pair = row["lang"] + "->" + row["meaningLang"]
        if pair in measurements["baseline"]:
            actual = measurements["baseline"][pair]["levels"][row["level"]]
            for key in ("eligibleTargets", "selectedTargets", "contextCollisionTargetsRejected"):
                if actual[key] != row[key]:
                    raise ValueError("baseline reproduction failed: " + row["deckId"] + " " + key)
    baseline_ids_by_level = {
        (pair, level): set(row["selectedIds"])
        for pair, result in measurements["baseline"].items()
        for level, row in result["levels"].items()
    }
    for label, pairs in measurements.items():
        for pair, result in pairs.items():
            for level, row in result["levels"].items():
                before = measurements["baseline"][pair]["levels"][level]
                ids = set(row["selectedIds"])
                baseline_ids = baseline_ids_by_level[pair, level]
                row["selectedCountDelta"] = row["selectedTargets"] - before["selectedTargets"]
                row["newSelectedTargetsAgainstBaseline"] = len(ids - baseline_ids)
                row["baselineSelectedTargetsNotSelected"] = len(baseline_ids - ids)
                row.pop("selectedIds")
    return {"reportVersion": 1, "publicationSafe": False, "sourceAdmissionChanged": False,
            "catalogueBuilt": False, "semanticAccuracyCertified": False,
            "scope": "pl<->ko; existing whole segments; baseline frequency space; unknown forms deferred",
            "baselineReproduced": True, "ranksFileSha256": sha(ranks_path), "baselineFileSha256": sha(baseline_path),
            "expectedSelectionSha256": sha(expected_path), "environment": prepare(["pl", "ko"]),
            "pythonVersion": platform.python_version(),
            "pipelineSha256": {name: sha(Path(__file__).parent / name) for name in
                               ("replenishment_review.py", "selection_experiment.py", "selection_policy.py",
                                "build_catalogue_v2.py", "catalogue_core.py", "segmentation.py", "ingest/model.py")},
            "inputFiles": manifest, "revisions": revisions, "wmt24pp": wmt_stats,
            "mkqa": {"queries": len(mkqa_rows), "scope": "optimistic all-query diagnostic before Everyday/domain review; answers never used"},
            "ntrex": {"collection": "knowledge", "alignedSegments": len(docs), "documents": len(set(docs)),
                      "shapeOnly": nt_shape, "netKnowledgeYieldMeasured": False},
            "measurements": measurements}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ("primary", "manifest", "revisions", "ranks", "baseline", "expected", "out"):
        ap.add_argument("--" + name, type=Path, required=True)
    args = ap.parse_args()
    if args.out.exists() or args.out.resolve().is_relative_to(args.primary.resolve()):
        raise ValueError("output must be new and outside source data")
    result = run(args.primary, args.manifest, args.revisions, args.ranks, args.baseline, args.expected)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    print(json.dumps({k: result[k] for k in ("scope", "baselineReproduced", "wmt24pp", "mkqa", "ntrex")}))


if __name__ == "__main__":
    main()
