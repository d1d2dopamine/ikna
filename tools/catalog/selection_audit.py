#!/usr/bin/env python3
"""Audit a saved Part 10 preview, never acquire a corpus or approve publication."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from difflib import SequenceMatcher
import gzip
import hashlib
import io
import itertools
import json
from pathlib import Path
import platform
import re
import zipfile

import catalogue_core as core
from catalogue_v2 import canonical_target, target_id
from segmentation import prepare, word_spans, utf16_length, utf16_offset, utf16_slice
from selection_policy import near_duplicate
from ingest.registry import SourceRegistry
from ingest.model import candidate_id

HERE = Path(__file__).resolve().parent
MEMBERS = ("reports/EVERYDAY-POOL.json", "reports/EVERYDAY-SELECTION.json",
           "review/everyday-selection-preview.jsonl.gz")
MAX_ARCHIVE = 32 * 1024 * 1024
MAX_LOGICAL = 64 * 1024 * 1024
SELECTION_FILES = ("everyday_selection.py", "everyday_pool.py", "selection_experiment.py", "selection_policy.py",
                   "build_catalogue_v2.py", "catalogue_core.py", "catalogue_v2.py", "segmentation.py",
                   "ingest/model.py", "ingest/registry.py")
FLAGS = {
    "ja-single-hiragana": "Один знак хираганы: проверить самостоятельность учебной цели",
    "similar-alternates": "Похожие альтернативы: проверить разнообразие контекстов",
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "duplicate JSON key: " + key)
            result[key] = value
        return result
    return json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError("non-finite JSON: " + value)))


def read_artifact(path, expected_sha=None):
    require(path.stat().st_size <= MAX_ARCHIVE, "review ZIP exceeds 32 MiB; do not pass the full pool")
    raw = path.read_bytes()
    digest = sha(raw)
    if expected_sha:
        require(re.fullmatch(r"[0-9a-f]{64}", expected_sha) is not None, "invalid expected ZIP SHA-256")
        require(digest == expected_sha, "review ZIP SHA-256 mismatch")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "duplicate ZIP member")
        data = {}
        for name in MEMBERS:
            require(name in names, "missing review member: " + name)
            require(archive.getinfo(name).file_size <= MAX_ARCHIVE, "oversized review member")
            data[name] = archive.read(name)  # CRC checked; never extract supplied paths.
    with gzip.GzipFile(fileobj=io.BytesIO(data[MEMBERS[2]])) as handle:
        logical = handle.read(MAX_LOGICAL + 1)
    require(len(logical) <= MAX_LOGICAL, "preview exceeds 64 MiB uncompressed")
    rows = [strict_json(line) for line in logical.splitlines() if line.strip()]
    require(0 < len(rows) <= 50000, "preview row count outside bounded audit scope")
    identity = {"archiveSha256": digest, "archiveBytes": len(raw),
                "members": {name: {"sha256": sha(value), "sizeBytes": len(value)} for name, value in data.items()},
                "previewLogicalSha256": sha(logical), "previewLogicalBytes": len(logical)}
    return strict_json(data[MEMBERS[0]]), strict_json(data[MEMBERS[1]]), rows, identity


def similar(a, b, lang):
    """Review aid only; intentionally does not change the Part 10 selector."""
    left = [canonical_target(s.surface) for s in word_spans(a, lang)]
    right = [canonical_target(s.surface) for s in word_spans(b, lang)]
    # Ordered tokens retain repetitions, unlike production's token-set check.
    tokens = SequenceMatcher(None, left, right, autojunk=False).ratio()
    characters = SequenceMatcher(None, canonical_target(a), canonical_target(b), autojunk=False).ratio()
    return tokens >= 0.85 and characters >= 0.85


def audit(pool, report, rows, identity):
    require(pool.get("part") == 7 and pool.get("reportVersion") == 1 and
            pool.get("status") == "admitted-source-pool-evidence" and
            pool.get("publicationSafe") is False and pool.get("sourceAdmissionPassed") is True and
            pool.get("completeInputScan") is True and
            pool.get("sourceDecision") == {"tatoeba": "admitted", "massive": "excluded"},
            "expected an admitted non-publishing Part 7 report")
    require(report.get("part") == 10 and report.get("reportVersion") == 1 and
            report.get("status") == "admitted-everyday-selection-review" and
            report.get("publicationSafe") is False, "expected a non-publishing Part 10 review")
    scope = report["reviewScope"]
    require(all(scope.get(k) is False for k in
                ("previewIsCompleteSelection", "materialReviewCompleted", "contentFrozen")), "unexpected review/freeze claim")
    admitted = report["admittedInput"]
    require(admitted["poolReportSha256"] == identity["members"][MEMBERS[0]]["sha256"], "Part 7 report hash mismatch")
    for key, pool_key in (("poolSha256", "sha256"), ("poolLogicalSha256", "logicalSha256"),
                          ("uniqueCandidates", "uniqueCandidates")):
        require(admitted[key] == pool["pool"][pool_key], "pool identity mismatch: " + key)
    for key in ("sourceVersion", "learn", "meanings", "registrySha256"):
        require(admitted[key] == pool[key], "admission scope mismatch: " + key)
    registry = HERE / "sources/catalogue-v2-sources.json"
    require(sha(registry.read_bytes()) == admitted["registrySha256"], "current source registry differs from admission")
    source_policy = SourceRegistry.load(registry).get("tatoeba")
    source_policy.require_publication_ready()  # Reuse existing attribution requirements; no new gate.
    require(admitted["poolEnvironment"] == pool["environment"] and
            admitted["poolPipelineSha256"] == pool["pipelineSha256"], "pool environment/pipeline mismatch")
    for key, allowed in (("learn", core.LEARNABLE), ("meanings", core.MEANINGS)):
        values = admitted[key]
        require(values == sorted(set(values)) and set(values) <= set(allowed), "invalid language scope")
    require(admitted["sourceVersion"] not in ("", "latest", "unknown"), "unpinned source version")
    require(report["stage"]["uniqueCandidates"] == admitted["uniqueCandidates"] and
            report["stage"]["inputCandidates"] == admitted["uniqueCandidates"] and
            report["stage"]["duplicateCandidates"] == 0, "selection input count mismatch")
    policy = report["policy"]
    require(report["morphologyUsed"] is False, "this auditor supports the exact-surface, non-morphological preview")
    require(policy["functionTop"] == pool["sieve"]["functionTop"], "function sieve mismatch")
    require(isinstance(policy["previewLimitPerDeck"], int) and 1 <= policy["previewLimitPerDeck"] <= 100 and
            policy["previewLimitPerDeck"] == scope["rowsPerPublishableDeck"], "invalid preview limit")
    expected = {f"{a}-{b}-everyday-{level}" for a in admitted["learn"] for b in admitted["meanings"]
                if a != b for level in core.LEVELS}
    require(report["plannedDirectedPairs"] * 3 == len(expected), "planned pair count mismatch")
    decks = {d["deckId"]: d for d in report["decks"]}
    require(len(decks) == len(report["decks"]) and set(decks) == expected, "incomplete/duplicate deck inventory")
    decisions = Counter(d["decision"] for d in decks.values())
    require(set(decisions) <= {"publish", "publish-thin", "omit-below-minimum", "omit-no-quality-supply"}, "unknown decision")
    for key, value in decisions.items():
        require(report["summary"].get(key, 0) == value, "decision total mismatch")
    for key in ("eligibleTargets", "selectedTargets", "budgetRejected", "nearDuplicateContextsRejected",
                "contextCollisionTargetsRejected"):
        require(report["summary"][key] == sum(d[key] for d in decks.values()), "deck sum mismatch: " + key)
    for d in decks.values():
        require(d["deckId"] == f"{d['lang']}-{d['meaningLang']}-everyday-{d['level']}" and
                d["collection"] == "everyday", "deck scope mismatch")
        require(0 <= d["selectedTargets"] <= d["eligibleTargets"] and
                d["selectedTargets"] <= policy["maxTargetsPerLevel"], "invalid deck counts")
        n = d["selectedTargets"]
        wanted = ("omit-no-quality-supply" if d["eligibleTargets"] == 0 else
                  "omit-below-minimum" if n < policy["minDeck"] else
                  "publish-thin" if n < policy["thinDeck"] else "publish")
        require(d["decision"] == wanted, "decision differs from recorded thresholds")
    pipeline = report["selectionPipelineSha256"]
    extra = {"japanese_boundary.py", "selection_output.py", "requirements-selection-quality.txt", "sources/selection-quarantine.json"}
    require(set(pipeline) == set(SELECTION_FILES) or
            (set(pipeline) == set(SELECTION_FILES) | extra and
             (report.get("qualityPolicy", {}).get("version") == 2 or "selectedMaterial" in report)),
            "unexpected selection pipeline manifest")
    # These determine identity/boundaries; other changed pipeline files are recorded
    # for diagnosis without pretending the old selection was regenerated here.
    for name in ("catalogue_core.py", "catalogue_v2.py", "segmentation.py"):
        require(pipeline[name] == sha((HERE / name).read_bytes()), "identity/boundary implementation differs: " + name)
    segmentation = prepare(admitted["learn"])
    require(segmentation == report["selectionEnvironment"]["segmentation"], "segmentation environment differs; reproduce recorded ICU")
    by_deck = Counter()
    seen = set()
    used_contexts = set()
    flags = Counter()
    flag_targets = defaultdict(set)
    cjk_one = defaultdict(set)
    global_ids = set()
    contexts_count = origins_count = case_variants = no_attribution = 0
    annotated = []
    for row in rows:
        deck = row["deckId"]
        require(deck in decks and decks[deck]["decision"] in ("publish", "publish-thin"), "row outside previewable deck")
        for key in ("lang", "meaningLang", "collection", "level"):
            require(row[key] == decks[deck][key], "row/deck scope mismatch")
        lang, text, tid = row["lang"], row["text"], row["targetId"]
        require(tid == target_id(lang, text), "global target identity mismatch")
        require(len(text) >= core.minimum_phrase(lang) and utf16_length(text) <= core.MAX_PHRASE, "invalid target length")
        key = (deck, tid)
        require(key not in seen, "duplicate preview membership")
        seen.add(key); global_ids.add(tid); by_deck[deck] += 1
        require(core.level_of(row["freqRank"]) == row["level"] and row["freqRank"] > policy["functionTop"], "target rank/level mismatch")
        require(row["lemmaKey"] == tid and row["lemmaSource"] == "unavailable", "unexpected inferred lemma")
        require(row["sourceFamilies"] == ["tatoeba"], "non-admitted target family")
        require(1 <= len(row["contexts"]) <= policy["contextsPerTarget"], "invalid contexts-per-target")
        require(row["evidenceContexts"] >= len(row["contexts"]), "invalid context evidence count")
        row_flags = []
        if lang == "ja" and re.fullmatch(r"[\u3041-\u3096]", text):
            row_flags.append("ja-single-hiragana")
        if lang in ("ja", "ko", "zh") and len(text) == 1:
            cjk_one[lang].add(tid)  # Informational; Chinese/Korean single-character words can be useful.
        visible = []
        for c in row["contexts"]:
            contexts_count += 1
            require(c["sourceFamilies"] == ["tatoeba"] and len(c["origins"]) > 0, "context missing admitted origins")
            require(isinstance(c["meaning"], str) and c["meaning"].strip() and
                    utf16_length(c["meaning"]) <= core.MAX_TRANSLATION, "invalid meaning text")
            require(isinstance(c["context"], str) and len(c["context"]) >= (6 if lang in core.CJK else core.MIN_SENTENCE) and
                    utf16_length(c["context"]) <= core.MAX_SENTENCE, "invalid context length")
            require(re.fullmatch(r"c1:everyday:" + re.escape(lang + "-" + row["meaningLang"]) + r":[0-9a-f]{20}", c["candidateId"]) is not None, "invalid candidate reference")
            require(c["candidateId"] == candidate_id("everyday", lang, row["meaningLang"], c["context"], c["meaning"]),
                    "candidate content hash mismatch")
            ck = (deck, c["contextId"])
            require(ck not in used_contexts, "context reused inside preview deck")
            used_contexts.add(ck)
            primary_found = False
            origin_keys = set()
            for o in c["origins"]:
                origins_count += 1
                source_policy.validate_origin_for_publication(o)
                require(o["sourceFamily"] == "tatoeba" and o["sourceVersion"] == admitted["sourceVersion"], "mixed origin family/version")
                require(all(re.fullmatch(r"tatoeba:[1-9][0-9]*", o[k]) for k in ("contextRef", "meaningRef")), "invalid source reference")
                ok = (o["contextRef"], o["meaningRef"])
                require(ok not in origin_keys, "duplicate context origin")
                origin_keys.add(ok)
                primary_found |= ok == (c["contextId"], c["meaningId"])
                if not all(o.get("attribution", {}).get(k) for k in ("contextContributor", "meaningContributor")):
                    no_attribution += 1
            require(primary_found, "primary references missing from origins")
            spans = word_spans(c["context"], lang)
            found = [s for s in spans if canonical_target(s.surface) == canonical_target(text)]
            require(len(found) == 1, "target is not exactly one complete token")
            s = found[0]
            start, end = utf16_offset(c["context"], s.start), utf16_offset(c["context"], s.end)
            require(utf16_slice(c["context"], start, end) == s.surface, "UTF-16 reconstruction failed")
            require(c["tokenCount"] == len(spans), "token count mismatch")
            case_variants += s.surface != text
            visible.append(dict(c, highlight={"start": start, "end": end, "surface": s.surface}))
        for a, b in itertools.combinations(row["contexts"], 2):
            require(not near_duplicate(a["context"], b["context"], policy["nearDuplicateJaccard"]), "production near-duplicate retained")
            if similar(a["context"], b["context"], lang) and "similar-alternates" not in row_flags:
                row_flags.append("similar-alternates")
        for flag in row_flags:
            flags[flag] += 1; flag_targets[flag].add(tid)
        annotated.append(dict(row, contexts=visible, auditFlags=row_flags))
    require(len(rows) == report["previewRows"], "preview row count mismatch")
    for deck, d in decks.items():
        expected_rows = min(policy["previewLimitPerDeck"], d["selectedTargets"]) if d["decision"] in ("publish", "publish-thin") else 0
        require(by_deck[deck] == expected_rows, "incomplete preview deck: " + deck)
    result = {
        "auditVersion": 1, "status": "preview-structural-checks-passed", "publicationSafe": False,
        "materialReviewCompleted": False, "contentFrozen": False, "input": identity,
        "sourceVersion": admitted["sourceVersion"], "poolIdentityReported": pool["pool"],
        "fullPoolBytesChecked": False, "environment": {"python": platform.python_version(), "segmentation": segmentation},
        "auditPipelineSha256": {n: sha((HERE / n).read_bytes()) for n in
                                ("selection_audit.py", "selection_review.html", "catalogue_core.py", "catalogue_v2.py",
                                 "segmentation.py", "selection_policy.py", "ingest/model.py", "ingest/registry.py",
                                 "sources/catalogue-v2-sources.json")},
        "selectionPipelineMatchesCurrent": {n: sha((HERE / n).read_bytes()) == v for n, v in pipeline.items()},
        "selectionReported": {"selectedMembershipsAllDecisions": report["summary"]["selectedTargets"],
                              "selectedMembershipsInPreviewableDecisions": sum(d["selectedTargets"] for d in decks.values()
                                                                             if d["decision"] in ("publish", "publish-thin")),
                              "decisions": dict(sorted(decisions.items()))},
        "preview": {"memberships": len(rows), "uniqueGlobalTargets": len(global_ids), "contexts": contexts_count,
                    "origins": origins_count, "caseVariantContexts": case_variants,
                    "originsWithoutBothContributors": no_attribution, "deckCounts": dict(sorted(by_deck.items())),
                    "membershipsByLearningLanguage": dict(sorted(Counter(r["lang"] for r in rows).items())),
                    "membershipsByLevel": dict(sorted(Counter(r["level"] for r in rows).items()))},
        "reviewFlags": {f: {"memberships": flags[f], "uniqueGlobalTargets": len(flag_targets[f]), "meaning": FLAGS[f]} for f in FLAGS},
        "singleCharacterCjkTargetsInformational": {l: len(ids) for l, ids in sorted(cjk_one.items())},
        "limitations": ["Rank-ordered first targets per deck; not a random/representative sample.",
                        "Pool hashes are linked through reports, not recalculated from the unavailable full pool.",
                        "Valid token boundaries are not evidence of pedagogical suitability or translation accuracy.",
                        "Review flags do not reject, rewrite, merge, publish or freeze content.",
                        "Frequency levels are not CEFR assessments; no global semantic-quality percentage is estimated."],
    }
    return result, annotated


def attach_notes(result, rows, notes):
    require(notes.get("archiveSha256") == result["input"]["archiveSha256"], "manual notes belong to another review ZIP")
    known = {(r["deckId"], r["targetId"]) for r in rows}
    findings = {}
    for entry in notes["cards"]:
        key = (entry["deckId"], entry["targetId"])
        require(key in known and key not in findings, "unknown/duplicate membership in manual notes")
        require(entry["status"] in ("illustrative", "needs-review", "counterexample") and
                isinstance(entry["note"], str) and 0 < len(entry["note"]) <= 10000, "invalid manual note")
        findings[key] = entry
    return [dict(row, manualFinding=findings.get((row["deckId"], row["targetId"]))) for row in rows]


def viewer(result, rows):
    payload = json.dumps({"audit": result, "rows": rows, "flagLabels": FLAGS}, ensure_ascii=False, separators=(",", ":"))
    # No source text can terminate the data script. Rendering uses textContent.
    payload = payload.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    template = (HERE / "selection_review.html").read_text(encoding="utf-8")
    return template.replace("__AUDIT_DATA__", payload)


def markdown(result):
    p = result["preview"]
    lines = ["# Saved Everyday preview audit", "", "Structural checks passed; no publication or content freeze.", "",
             f"Source: `{result['sourceVersion']}`", f"Review ZIP SHA-256: `{result['input']['archiveSha256']}`", "",
             f"Preview: {p['memberships']} memberships / {p['uniqueGlobalTargets']} unique global targets / {p['contexts']} contexts / {p['origins']} origins.",
             f"Case-variant presentations: {p['caseVariantContexts']}; all resolve to one complete canonical target token.", "",
             "## Review-only signals", "", "| Signal | Memberships | Unique global targets |", "|---|---:|---:|"]
    for flag, counts in result["reviewFlags"].items():
        lines.append(f"| {flag} | {counts['memberships']} | {counts['uniqueGlobalTargets']} |")
    lines.extend(["", "## Evidence limits", ""] + ["- " + s for s in result["limitations"]])
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review-zip", type=Path, required=True)
    parser.add_argument("--expected-sha256")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--notes", type=Path, help="optional manual findings bound to the same review ZIP")
    args = parser.parse_args()
    # Use a new directory: never overwrite inputs, prior reviews, or source files.
    try:
        require(not args.output_dir.exists(), "output directory must not exist")
        pool, report, rows, identity = read_artifact(args.review_zip, args.expected_sha256)
        result, annotated = audit(pool, report, rows, identity)
        if args.notes:
            require(args.notes.stat().st_size <= MAX_ARCHIVE, "manual notes exceed 32 MiB")
            notes_raw = args.notes.read_bytes()
            annotated = attach_notes(result, annotated, strict_json(notes_raw))
            result["manualNotesSha256"] = sha(notes_raw)
        args.output_dir.mkdir(parents=True)
        (args.output_dir / "AUDIT.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (args.output_dir / "AUDIT.md").write_text(markdown(result), encoding="utf-8")
        (args.output_dir / "CARDS.html").write_text(viewer(result, annotated), encoding="utf-8")
    except (ValueError, OSError, KeyError, TypeError, zipfile.BadZipFile, EOFError) as error:
        print("ERROR:", error)
        return 1
    print(json.dumps({"preview": {k: result["preview"][k] for k in
                                 ("memberships", "uniqueGlobalTargets", "contexts", "origins")},
                      "reviewFlags": result["reviewFlags"]}, ensure_ascii=False, indent=2))
    print("Review only; full pool bytes not checked; publicationSafe=false")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
