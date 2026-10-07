#!/usr/bin/env python3
"""Stream-check full selected material and compare a bound saved baseline.

No corpus acquisition, human-review queue, translation truth score or publication.
Counts compare complete reports. Identity/context changes cover only baseline
preview memberships: the old complete selection was never retained.
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import tempfile

import catalogue_core as core
from catalogue_v2 import canonical_target, target_id
from ingest.model import candidate_id
from ingest.registry import SourceRegistry
from japanese_boundary import JapaneseBoundaryGuard
from segmentation import prepare, word_spans, utf16_length, utf16_offset, utf16_slice
import selection_audit as preview_audit
from selection_policy import near_duplicate, context_overlap, quarantine_reason

HERE = Path(__file__).resolve().parent
MAX_ROW_BYTES = 16 * 1024 * 1024  # Keep complete merged origins; this is an I/O bound, not a content score.
require = preview_audit.require
sha = preview_audit.sha
strict_json = preview_audit.strict_json


def load_baseline(directory, expected_report_sha):
    data = {}
    for name in preview_audit.MEMBERS:
        path = directory / name
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= preview_audit.MAX_ARCHIVE,
                "missing/oversized baseline file: " + name)
        data[name] = path.read_bytes()
    require(sha(data[preview_audit.MEMBERS[1]]) == expected_report_sha, "baseline selection report SHA-256 mismatch")
    with gzip.open(directory / preview_audit.MEMBERS[2], "rb") as handle:
        logical = handle.read(preview_audit.MAX_LOGICAL + 1)
    require(len(logical) <= preview_audit.MAX_LOGICAL, "baseline preview is not bounded")
    rows = [strict_json(line) for line in logical.splitlines() if line.strip()]
    require(0 < len(rows) <= 50000, "baseline preview row count outside audit scope")
    identity = {"archiveSha256": None, "container": "extracted-artifact-files", "previewLogicalSha256": sha(logical),
                "members": {n: {"sha256": sha(b), "sizeBytes": len(b)} for n, b in data.items()}}
    pool, report = strict_json(data[preview_audit.MEMBERS[0]]), strict_json(data[preview_audit.MEMBERS[1]])
    preview_audit.audit(pool, report, rows, identity)
    return report, rows


def evaluate(material, current, baseline, old_rows):
    require(current.get("publicationSafe") is False and current.get("status") == "admitted-everyday-selection-review",
            "expected a non-publishing admitted selection")
    require(current.get("qualityPolicy", {}).get("id") == "boundary-diversity-v2", "expected the explicit quality policy v2")
    require(current["admittedInput"] == baseline["admittedInput"], "baseline and current selection have different exact input pins/scope")
    for key in ("functionTop", "maxTargetsPerLevel", "minDeck", "thinDeck", "contextsPerTarget", "nearDuplicateJaccard"):
        require(current["policy"][key] == baseline["policy"][key], "comparison changed unrelated policy: " + key)
    expected_pipeline = set(preview_audit.SELECTION_FILES) | {"japanese_boundary.py", "selection_output.py", "requirements-selection-quality.txt", "sources/selection-quarantine.json"}
    require(set(current["selectionPipelineSha256"]) == expected_pipeline, "unexpected current pipeline manifest")
    for name, digest in current["selectionPipelineSha256"].items():
        require(sha((HERE / name).read_bytes()) == digest, "current pipeline fingerprint mismatch: " + name)
    identity = current["selectedMaterial"]
    with material.open("rb") as handle:
        require(hashlib.file_digest(handle, "sha256").hexdigest() == identity["sha256"] and
                material.stat().st_size == identity["sizeBytes"], "selected material raw identity mismatch")
    decks = {d["deckId"]: d for d in current["decks"]}
    old_decks = {d["deckId"]: d for d in baseline["decks"]}
    require(len(decks) == len(current["decks"]) and set(decks) == set(old_decks), "comparison deck inventory mismatch")
    expected = {key: d["selectedTargets"] for key, d in decks.items() if d["decision"] in ("publish", "publish-thin")}
    require(identity["completeIncludedDecisions"] is True and identity["publicationSafe"] is False, "incomplete/unsafe material handoff")
    require(identity["decks"] == expected and identity["memberships"] == sum(expected.values()), "material/report count mismatch")
    require(all(current["summary"][key] == sum(d[key] for d in decks.values()) for key in
                ("selectedTargets", "eligibleTargets", "budgetRejected", "nearDuplicateContextsRejected", "contextCollisionTargetsRejected")),
            "current report deck totals mismatch")
    require(current["stage"] == baseline["stage"], "candidate/context staging differs from baseline")
    admission = current["admittedInput"]
    require(prepare(admission["learn"]) == current["selectionEnvironment"]["segmentation"], "selected material ICU environment mismatch")
    registry = HERE / "sources/catalogue-v2-sources.json"
    require(sha(registry.read_bytes()) == admission["registrySha256"], "registry admission pin mismatch")
    policy = SourceRegistry.load(registry).get("tatoeba"); policy.require_publication_ready()
    guard = JapaneseBoundaryGuard() if "ja" in admission["learn"] else None
    if guard:
        require(guard.evidence == current["qualityPolicy"]["japaneseBoundary"], "Japanese boundary environment differs")
    old = {(r["deckId"], r["targetId"]): r for r in old_rows}
    retained = {}
    logical = hashlib.sha256(); logical_bytes = contexts = origins = similar_alternates = memberships = 0
    counts = Counter(); one_character = Counter()
    try:
        with tempfile.TemporaryDirectory(prefix="selection-evaluate-") as td:
            db = sqlite3.connect(Path(td) / "identity.sqlite3")
            try:
                db.executescript("CREATE TABLE membership(deck TEXT, target TEXT, PRIMARY KEY(deck,target));"
                                 "CREATE TABLE context(deck TEXT, ref TEXT, PRIMARY KEY(deck,ref));"
                                 "CREATE TABLE target(id TEXT PRIMARY KEY);")
                with gzip.open(material, "rb") as handle:
                    while raw := handle.readline(MAX_ROW_BYTES + 1):
                        require(len(raw) <= MAX_ROW_BYTES and raw.strip() and raw.endswith(b"\n"), "invalid/oversized selected JSONL row")
                        memberships += 1
                        require(memberships <= identity["memberships"], "material exceeds reported row count")
                        logical.update(raw); logical_bytes += len(raw)
                        row = strict_json(raw); deck = row["deckId"]; tid = row["targetId"]; lang = row["lang"]
                        require(deck in expected, "material contains an omitted/out-of-scope deck")
                        require(all(row[k] == decks[deck][k] for k in ("lang", "meaningLang", "collection", "level")), "material deck scope mismatch")
                        require(row["collection"] == "everyday" and tid == target_id(lang, row["text"]), "material target identity mismatch")
                        require(len(row["text"]) >= core.minimum_phrase(lang) and utf16_length(row["text"]) <= core.MAX_PHRASE, "material target length mismatch")
                        require(core.level_of(row["freqRank"]) == row["level"] and row["freqRank"] > current["policy"]["functionTop"], "material rank/level mismatch")
                        require(row["lemmaKey"] == tid and row["lemmaSource"] == "unavailable", "unexpected target lemma rewriting")
                        require(1 <= len(row["contexts"]) <= current["policy"]["contextsPerTarget"], "material context limit mismatch")
                        db.execute("INSERT INTO membership VALUES(?,?)", (deck, tid))
                        db.execute("INSERT OR IGNORE INTO target VALUES(?)", (tid,))
                        counts[deck] += 1
                        if lang in ("ja", "zh", "ko") and len(row["text"]) == 1: one_character[lang] += 1
                        for c in row["contexts"]:
                            contexts += 1
                            db.execute("INSERT INTO context VALUES(?,?)", (deck, c["contextId"]))
                            require(c["candidateId"] == candidate_id("everyday", lang, row["meaningLang"], c["context"], c["meaning"]), "material candidate content hash mismatch")
                            require(c["sourceFamilies"] == ["tatoeba"] and row["sourceFamilies"] == ["tatoeba"], "unadmitted material family")
                            require(len(c["context"]) >= (6 if lang in core.CJK else core.MIN_SENTENCE) and
                                    utf16_length(c["context"]) <= core.MAX_SENTENCE and
                                    c["meaning"].strip() and utf16_length(c["meaning"]) <= core.MAX_TRANSLATION,
                                    "material sentence/meaning limits mismatch")
                            require(len(c["origins"]) > 0, "material origin missing")
                            require(not quarantine_reason(c["context"], c["meaning"], c["origins"]), "selected material contains an exact quarantined source case")
                            found = [s for s in word_spans(c["context"], lang) if canonical_target(s.surface) == canonical_target(row["text"])]
                            require(len(found) == 1, "material target is not one complete canonical token")
                            s = found[0]
                            require(utf16_slice(c["context"], utf16_offset(c["context"], s.start), utf16_offset(c["context"], s.end)) == s.surface, "material UTF-16 failure")
                            if lang == "ja":
                                keep, rejected, _ = guard.filter(c["context"], [(row["freqRank"], s.surface, tid, row["level"])])
                                require(len(keep) == 1 and not rejected, "Japanese selected target cuts a morpheme")
                            refs = set()
                            for o in c["origins"]:
                                origins += 1; policy.validate_origin_for_publication(o)
                                require(o["sourceFamily"] == "tatoeba" and o["sourceVersion"] == admission["sourceVersion"], "material origin family/version mismatch")
                                require(all(re.fullmatch(r"tatoeba:[1-9][0-9]*", o[k]) for k in ("contextRef", "meaningRef")), "material source reference invalid")
                                pair = (o["contextRef"], o["meaningRef"])
                                require(pair not in refs, "duplicate material origin"); refs.add(pair)
                            require((c["contextId"], c["meaningId"]) in refs, "material primary origin missing")
                        for i, a in enumerate(row["contexts"]):
                            for b in row["contexts"][i + 1:]:
                                require(not near_duplicate(a["context"], b["context"], current["policy"]["nearDuplicateJaccard"]), "material violates duplicate policy")
                                similar_alternates += context_overlap(a["context"], b["context"], lang) >= 0.9
                        key = (deck, tid)
                        if key in old:
                            retained[key] = {"sameContextIds": [c["contextId"] for c in row["contexts"]] == [c["contextId"] for c in old[key]["contexts"]],
                                             "text": row["text"], "deckId": deck, "targetId": tid,
                                             "before": old[key]["contexts"], "after": row["contexts"]}
                        if memberships % 10000 == 0: db.commit()
                db.commit()
                unique = db.execute("SELECT COUNT(*) FROM target").fetchone()[0]
            finally:
                db.close()
    finally:
        if guard: guard.close()
    require(dict(counts) == expected and contexts == identity["contexts"] and origins == identity["origins"] and
            logical.hexdigest() == identity["logicalSha256"] and logical_bytes == identity["logicalSizeBytes"],
            "selected material logical count/hash mismatch")
    changes = [{"deckId": k, "beforeDecision": old_decks[k]["decision"], "afterDecision": decks[k]["decision"],
                "beforeSelected": old_decks[k]["selectedTargets"], "afterSelected": decks[k]["selectedTargets"],
                "delta": decks[k]["selectedTargets"] - old_decks[k]["selectedTargets"]} for k in sorted(decks)]
    lost_pairs = sorted({(d["lang"], d["meaningLang"]) for d in old_decks.values() if d["decision"].startswith("publish")} -
                        {(d["lang"], d["meaningLang"]) for d in decks.values() if d["decision"].startswith("publish")})
    return {"evaluationVersion": 1, "status": "complete-selected-material-contracts-passed", "publicationSafe": False,
            "qualityGatePassed": not lost_pairs, "automaticChecksCompleted": True, "semanticAccuracyCertified": False,
            "materialReviewCompleted": False, "contentFrozen": False, "sourceVersion": admission["sourceVersion"],
            "admittedInput": admission, "selectedMaterial": identity,
            "evaluationPipelineSha256": {n: sha((HERE / n).read_bytes()) for n in
                ("selection_evaluate.py", "selection_audit.py", "japanese_boundary.py", "selection_policy.py", "catalogue_core.py",
                 "catalogue_v2.py", "segmentation.py", "ingest/model.py", "ingest/registry.py", "requirements-selection-quality.txt", "sources/selection-quarantine.json")},
            "memberships": sum(counts.values()), "uniqueGlobalTargets": unique, "contexts": contexts, "origins": origins,
            "similarAlternatePairsInformational": similar_alternates, "singleCharacterCjkMembershipsInformational": dict(one_character),
            "lostPreviouslyIncludedPairs": [f"{a}->{b}" for a, b in lost_pairs], "decks": changes,
            "baselinePreviewComparison": {"memberships": len(old), "retained": len(retained), "absent": len(old) - len(retained),
                                          "retainedWithChangedContexts": sum(not v["sameContextIds"] for v in retained.values()),
                                          "absentSamples": [{"deckId": k[0], "targetId": k[1], "text": r["text"], "before": r["contexts"]} for k, r in sorted(old.items()) if k not in retained][:30],
                                          "changedSamples": [v for k, v in sorted(retained.items()) if not v["sameContextIds"]][:30]},
            "limitations": ["Complete count deltas compare bound full reports; exact identity/context deltas cover only the old preview.",
                            "Lexical boundaries, provenance and structural checks do not certify translation truth or every pedagogical choice.",
                            "A lost previously included language pair stops automatic progression; counts/semantic quality are separate.",
                            "No new manual-review prerequisite is introduced. Census/freeze/storage and publication remain separate stages."]}


def markdown(report):
    lines = ["# Everyday selection quality evaluation", "", f"Automatic gate: **{report['qualityGatePassed']}**; publicationSafe=false; no semantic certification.",
             f"Full material: {report['memberships']} memberships, {report['uniqueGlobalTargets']} unique targets, {report['contexts']} contexts.", "",
             "## Full-report changes", "", "| Deck | Before | After | Delta |", "|---|---:|---:|---:|"]
    lines += [f"| {d['deckId']} | {d['beforeSelected']} | {d['afterSelected']} | {d['delta']} |" for d in report["decks"]]
    lines += ["", "## Baseline preview evidence", "", json.dumps({k: v for k, v in report["baselinePreviewComparison"].items() if not isinstance(v, list)}), "",
              "Only the old preview has exact before/after target evidence. See JSON for source-bound examples.", ""]
    lines += ["- " + line for line in report["limitations"]]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--selected", type=Path, required=True)
    parser.add_argument("--selection-report", type=Path, required=True)
    parser.add_argument("--baseline-directory", type=Path, required=True)
    parser.add_argument("--baseline-report-sha256", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        require(not args.output_dir.exists(), "evaluation output directory must not exist")
        current = strict_json(args.selection_report.read_bytes())
        baseline, old_rows = load_baseline(args.baseline_directory, args.baseline_report_sha256)
        result = evaluate(args.selected, current, baseline, old_rows)
        result["selectionReportFileSha256"] = sha(args.selection_report.read_bytes())
        result["baselineReportFileSha256"] = args.baseline_report_sha256
        args.output_dir.mkdir(parents=True)
        (args.output_dir / "QUALITY.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (args.output_dir / "QUALITY.md").write_text(markdown(result), encoding="utf-8")
        return 0 if result["qualityGatePassed"] else 2
    except (ValueError, OSError, KeyError, TypeError, sqlite3.IntegrityError, EOFError) as error:
        print("ERROR:", error)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
