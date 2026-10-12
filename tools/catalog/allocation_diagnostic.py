#!/usr/bin/env python3
"""Non-publishing full-evidence allocation audit; emits metadata, never corpus text.

Read the complete pinned pool for global ranks, retain candidate rows only for
the historical allocation-limited directions, and replay the unchanged selector.
The CLI has no fixture, relaxed-gate, download or production-policy switch.
"""
from __future__ import annotations

import argparse
from collections import Counter
from contextlib import closing, redirect_stderr
import gzip
import hashlib
import io
import json
import platform
from pathlib import Path
import sqlite3
import sys
import zlib

import catalogue_core as core
from catalogue_v2 import canonical_target, target_id
import everyday_selection as admitted
from everyday_pool import DEFAULT_REGISTRY, sha256
from ingest.model import Candidate
from japanese_boundary import JapaneseBoundaryGuard
import selection_experiment as selection
from selection_policy import choose_contexts, near_duplicate, select_target_ids
from segmentation import prepare, utf16_length, utf16_offset, utf16_slice, word_spans

HERE = Path(__file__).resolve().parent
HISTORICAL_LOGICAL = "7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4"
EVIDENCE_LIMIT = 12
SOURCE_FILES = (
    "everyday_selection.py", "everyday_pool.py", "selection_experiment.py", "selection_policy.py",
    "build_catalogue_v2.py", "catalogue_core.py", "catalogue_v2.py", "segmentation.py",
    "ingest/model.py", "ingest/registry.py", "japanese_boundary.py", "selection_output.py",
    "requirements-selection-quality.txt", "sources/selection-quarantine.json",
)
MAX_ROW_BYTES = 4 * 1024 * 1024


class DiagnosticError(ValueError):
    """Only controlled reason codes may be displayed by the CLI."""
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def require(condition, code):
    if not condition:
        raise DiagnosticError(code)


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def digest(value):
    return hashlib.sha256(encoded(value)).hexdigest()


def write_json(path, value):
    with Path(path).open("xb") as handle:
        handle.write(encoded(value) + b"\n")


def read_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except Exception:
        raise DiagnosticError("invalid-metadata-json") from None


def scope_from_report(report, enforce_historical=True):
    rows = report.get("decks")
    require(isinstance(rows, list) and rows, "missing-deck-inventory")
    inventory = {}
    for row in rows:
        require(isinstance(row, dict), "invalid-deck-row")
        key = row.get("deckId")
        require(isinstance(key, str) and key not in inventory, "duplicate-or-invalid-deck-id")
        require(key == f"{row.get('lang')}-{row.get('meaningLang')}-everyday-{row.get('level')}" and
                row.get("collection") == "everyday" and row.get("level") in core.LEVELS,
                "invalid-deck-scope")
        for field in ("eligibleTargets", "selectedTargets", "budgetRejected", "contextCollisionTargetsRejected",
                      "nearDuplicateContextsRejected", "morphologyDeferred"):
            require(type(row.get(field)) is int and row[field] >= 0, "invalid-deck-counter")
        inventory[key] = row
    decks = {k: d for k, d in inventory.items() if d["eligibleTargets"] >= 1000 and d["selectedTargets"] < 1000}
    require(decks and all(d["budgetRejected"] == 0 for d in decks.values()), "not-allocation-limited-scope")
    pairs = sorted({(d["lang"], d["meaningLang"]) for d in decks.values()})
    languages = sorted({a for a, _ in pairs})
    pair_rows = {}
    for a, b in pairs:
        expected = [inventory.get(f"{a}-{b}-everyday-{level}") for level in core.LEVELS]
        require(all(expected), "incomplete-pair-level-inventory")
        stats = expected[0].get("pairStats")
        require(isinstance(stats, dict) and type(stats.get("candidateRows")) is int and stats["candidateRows"] > 0,
                "invalid-pair-counters")
        require(all(d.get("pairStats") == stats for d in expected), "inconsistent-pair-counters")
        pair_rows[a + "-" + b] = stats["candidateRows"]
    if enforce_historical:
        require((len(decks), len(pairs), len(languages), sum(pair_rows.values())) == (20, 13, 8, 33515),
                "historical-diagnostic-scope-drift")
    return {"decks": decks, "inventory": inventory, "pairs": pairs, "languages": languages, "pairRows": pair_rows}


def validate_paths(args):
    inputs = [Path(getattr(args, k)).resolve() for k in ("pool", "pool_report", "selection_report", "registry")]
    require(all(p.is_file() for p in inputs), "missing-input-file")
    require(len(set(inputs)) == len(inputs), "aliased-input-paths")
    require(not any(a.samefile(b) for i, a in enumerate(inputs) for b in inputs[i + 1:]), "aliased-input-files")
    outputs = [Path(args.scratch).resolve(), Path(args.out).resolve()]
    require(all(not p.exists() for p in outputs), "output-directory-already-exists")
    require(outputs[0] != outputs[1] and not any(a in b.parents for a, b in (outputs, outputs[::-1])),
            "overlapping-output-directories")
    require(not any(p == o or o in p.parents or p in o.parents for p in inputs for o in outputs),
            "output-overlaps-input")
    return inputs, outputs


def file_bindings(paths):
    return {str(p): {"sha256": sha256(p), "sizeBytes": p.stat().st_size} for p in paths}


def verify_source_pins(report):
    expected = report.get("selectionPipelineSha256")
    require(isinstance(expected, dict) and set(expected) == set(SOURCE_FILES), "selection-source-manifest-scope")
    actual = {name: sha256(HERE / name) for name in SOURCE_FILES}
    require(actual == expected, "selection-source-fingerprint-mismatch")
    require(selection.parser().get_default("evidence_context_limit") == EVIDENCE_LIMIT,
            "evidence-memory-default-drift")
    return actual


def verify_report(report, pool_report, identity):
    require(report.get("reportVersion") == 1 and report.get("part") == 10 and
            report.get("status") == "admitted-everyday-selection-review" and
            report.get("publicationSafe") is False and report.get("morphologyUsed") is False,
            "unaccepted-selection-report")
    require(report.get("admittedInput") == identity, "selection-pool-input-pins-mismatch")
    require(report.get("selectedMaterial", {}).get("logicalSha256") == HISTORICAL_LOGICAL,
            "historical-selection-identity-mismatch")
    require(report.get("stage", {}).get("uniqueCandidates") == identity["uniqueCandidates"] == 11099490 and
            report["stage"].get("duplicateCandidates") == 0, "historical-pool-count-mismatch")
    policy = report.get("policy", {})
    expected = {"maxTargetsPerLevel": 8000, "minDeck": 40, "thinDeck": 1000, "contextsPerTarget": 3,
                "nearDuplicateJaccard": .85, "functionTop": 60}
    require(all(policy.get(k) == v for k, v in expected.items()), "historical-selection-policy-mismatch")
    require(pool_report.get("sieve", {}).get("functionTop") == policy["functionTop"], "function-rank-policy-mismatch")
    quality = report.get("qualityPolicy", {})
    require(quality.get("id") == "boundary-diversity-v2" and quality.get("version") == 2 and
            quality.get("semanticAccuracyCertified") is False, "quality-policy-mismatch")
    require(quality.get("sourceQuarantine", {}).get("sha256") == sha256(HERE / "sources/selection-quarantine.json"),
            "quarantine-fingerprint-mismatch")


def verify_runtime(report):
    segmentation = prepare(report["admittedInput"]["learn"])
    require(segmentation == report.get("selectionEnvironment", {}).get("segmentation"), "segmentation-environment-mismatch")
    guard = JapaneseBoundaryGuard()
    try:
        require(guard.evidence == report["qualityPolicy"].get("japaneseBoundary"), "japanese-environment-mismatch")
    except BaseException:
        guard.close()
        raise
    current = {"python": platform.python_version(), "zlib": zlib.ZLIB_RUNTIME_VERSION,
               "sqlite": sqlite3.sqlite_version, "segmentation": segmentation, "japaneseBoundary": guard.evidence}
    current["nonBlockingRuntimeDifferences"] = {k: {"baseline": report["selectionEnvironment"].get(k), "current": current[k]}
                                                for k in ("python", "zlib") if current[k] != report["selectionEnvironment"].get(k)}
    return current, guard


def stage_scoped(pool, global_path, subset_path, pairs, langs, batch_size=5000):
    """Exact original context key/first insertion; never rerank the pair subset."""
    pool, global_path, subset_path = Path(pool), Path(global_path), Path(subset_path)
    require(not global_path.exists() and not subset_path.exists() and global_path.resolve() != subset_path.resolve(),
            "staging-destination-exists-or-aliases")
    require(pool.resolve() not in (global_path.resolve(), subset_path.resolve()), "staging-overwrites-input")
    wanted, languages = set(map(tuple, pairs)), set(langs)
    global_db, subset = sqlite3.connect(global_path), sqlite3.connect(subset_path)
    stats = Counter(); context_batch, candidate_batch = [], []
    try:
        global_db.executescript("PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA temp_store=FILE;"
                               "PRAGMA cache_size=-65536; CREATE TABLE context(collection TEXT,lang TEXT,context_key TEXT,context TEXT,"
                               "PRIMARY KEY(collection,lang,context_key));")
        subset.executescript(selection.SCHEMA)
        def flush():
            global_db.executemany("INSERT OR IGNORE INTO context VALUES(?,?,?,?)", context_batch)
            subset.executemany("INSERT INTO candidate VALUES(?,?,?,?,?,?,?,?,?)", candidate_batch)
            context_batch.clear(); candidate_batch.clear()
        opener = gzip.open if pool.suffix == ".gz" else open
        with opener(pool, "rb") as handle:
            while raw := handle.readline(MAX_ROW_BYTES + 1):
                require(len(raw) <= MAX_ROW_BYTES, "oversized-candidate-row")
                if not raw.strip():
                    continue
                try:
                    record = Candidate.from_dict(json.loads(raw))
                except Exception:
                    raise DiagnosticError("invalid-candidate-during-staging") from None
                stats["inputCandidates"] += 1
                if record.lang not in languages:
                    continue
                key = selection._context_key(record.collection, record.lang, record.context)
                context_batch.append((record.collection, record.lang, key, record.context))
                if (record.lang, record.meaning_lang) in wanted:
                    origins = [o.to_dict() for o in record.origins]
                    scores = [o.alignment_score for o in record.origins if o.alignment_score is not None]
                    candidate_batch.append((record.id, record.collection, record.lang, record.meaning_lang, key,
                                            record.context, record.meaning,
                                            json.dumps(origins, ensure_ascii=False, separators=(",", ":")),
                                            max(scores) if scores else None))
                    stats["retainedCandidateRows"] += 1
                if len(context_batch) >= batch_size:
                    flush()
        flush(); global_db.commit(); subset.commit()
        subset.executescript(selection.POST_STAGE_INDEXES)
        # The first representative may come from an excluded meaning direction.
        for collection, lang, key in subset.execute("SELECT DISTINCT collection,lang,context_key FROM candidate ORDER BY collection,lang,context_key"):
            row = global_db.execute("SELECT context FROM context WHERE collection=? AND lang=? AND context_key=?",
                                    (collection, lang, key)).fetchone()
            require(row is not None, "global-context-representative-missing")
            subset.execute("INSERT INTO context VALUES(?,?,?,?)", (collection, lang, key, row[0]))
        subset.commit()
        stats["globalRankContexts"] = global_db.execute("SELECT COUNT(*) FROM context").fetchone()[0]
        stats["retainedAnalysisContexts"] = subset.execute("SELECT COUNT(*) FROM context").fetchone()[0]
        return dict(stats)
    except sqlite3.IntegrityError:
        raise DiagnosticError("duplicate-retained-candidate-identity") from None
    finally:
        global_db.close(); subset.close()


def allocate_level(targets, lang, policy):
    """Literal allocation traversal from build_report, with a read-only trace."""
    ordered, selection_stats = select_target_ids(targets, max(1, len(targets)))
    selected, lost, owners = [], [], {}
    near_rejected = collision_rejected = budget_rejected = 0
    used = set()
    for index, target in enumerate(ordered):
        if len(selected) >= policy["maxTargetsPerLevel"]:
            budget_rejected = len(ordered) - index
            break
        unused = [c for c in target["contexts"] if str(c.get("contextId") or c.get("candidateId")) not in used]
        if not unused:
            collision_rejected += 1; lost.append(target); continue
        contexts, rejected = choose_contexts(unused, policy["contextsPerTarget"], policy["nearDuplicateJaccard"], diverse=True, lang=lang)
        near_rejected += rejected
        if not contexts:
            collision_rejected += 1; lost.append(target); continue
        selected.append({k: target[k] for k in ("targetId", "text", "freqRank", "lemmaKey", "lemmaSource", "evidenceContexts", "sourceFamilies")}
                        | {"contexts": contexts})
        for i, c in enumerate(contexts):
            key = str(c.get("contextId") or c.get("candidateId"))
            require(key not in owners, "baseline-reuses-context-resource")
            used.add(key); owners[key] = {"targetId": target["targetId"], "role": "primary" if i == 0 else "alternate"}
    stats = {"selectedTargets": len(selected), "eligibleTargets": len(targets),
             "morphologyDeferred": selection_stats["morphologyDeferred"], "nearDuplicateContextsRejected": near_rejected,
             "contextCollisionTargetsRejected": collision_rejected, "budgetRejected": budget_rejected}
    return selected, stats, lost, owners


def compare_baseline(expected_deck, targets, stats, pair_stats):
    require(len(targets) == expected_deck["eligibleTargets"], "baseline-eligible-count-mismatch")
    require(all(expected_deck.get(k) == v for k, v in stats.items()), "baseline-allocation-count-mismatch")
    require(expected_deck["pairStats"] == dict(pair_stats), "baseline-pair-counters-mismatch")
    count = stats["selectedTargets"]
    decision = "omit-no-quality-supply" if count == 0 else "omit-below-minimum" if count < 40 else "publish-thin" if count < 1000 else "publish"
    require(expected_deck["decision"] == decision, "baseline-decision-mismatch")


def persist_full_graph(graphdb, full_targets):
    graphdb.executescript("CREATE TABLE full_context(target_id TEXT,ordinal INTEGER,context_id TEXT,candidate_id TEXT,payload TEXT,"
                         "PRIMARY KEY(target_id,ordinal)); CREATE INDEX full_context_target ON full_context(target_id,context_id);")
    edges = 0
    for target in full_targets:
        require(len(target["contexts"]) == target["evidenceContexts"], "unpruned-evidence-count-mismatch")
        for ordinal, c in enumerate(target["contexts"]):
            graphdb.execute("INSERT INTO full_context VALUES(?,?,?,?,?)",
                            (target["targetId"], ordinal, str(c.get("contextId") or c.get("candidateId")),
                             c["candidateId"], encoded(c).decode()))
            edges += 1
    graphdb.commit()
    return edges


def full_contexts(graphdb, tid):
    return [json.loads(row[0]) for row in graphdb.execute("SELECT payload FROM full_context WHERE target_id=? ORDER BY ordinal", (tid,))]


def audit_deck(bounded_targets, selected_rows, lost_targets, graphdb, lang, policy):
    before = digest(selected_rows)
    baseline_ids = {row["targetId"] for row in selected_rows}
    require(len(baseline_ids) == len(selected_rows), "duplicate-baseline-target-membership")
    owners = {}
    for row in selected_rows:
        for i, c in enumerate(row["contexts"]):
            key = str(c.get("contextId") or c.get("candidateId"))
            require(key not in owners, "baseline-reuses-context-resource")
            owners[key] = "primary" if i == 0 else "alternate"
    used = set(owners); losses, witnesses = [], []
    available_histogram = Counter(); graph_counts = Counter()
    for target in bounded_targets:
        full = full_contexts(graphdb, target["targetId"])
        raw_by_id = {c["candidateId"]: c for c in full}
        require(len(raw_by_id) == len(full), "duplicate-target-candidate-edge")
        require(all(c["candidateId"] in raw_by_id and raw_by_id[c["candidateId"]] == c for c in target["contexts"]),
                "bounded-evidence-not-contained-in-full-graph")
        graph_counts["fullEvidenceVariants"] += len(full)
        graph_counts["boundedEvidenceVariants"] += len(target["contexts"])
        graph_counts["prunedEvidenceVariants"] += len(full) - len(target["contexts"])
    for target in lost_targets:
        require(target["targetId"] not in baseline_ids, "lost-target-already-selected")
        full = full_contexts(graphdb, target["targetId"])
        bounded = target["contexts"]
        raw_ids = {str(c.get("contextId") or c.get("candidateId")) for c in full}
        bounded_ids = {str(c.get("contextId") or c.get("candidateId")) for c in bounded}
        require(not bounded_ids - set(owners), "lost-target-has-unused-bounded-context")
        baseline_free = [c for c in full if str(c.get("contextId") or c.get("candidateId")) not in owners]
        available, _ = choose_contexts(baseline_free, 3, policy["nearDuplicateJaccard"], diverse=True, lang=lang)
        available_count = len({str(c.get("contextId") or c.get("candidateId")) for c in available})
        available_histogram[str(available_count)] += 1
        losses.append({"targetId": target["targetId"], "freqRank": target["freqRank"], "level": target["level"],
                       "fullEvidenceVariants": len(full), "boundedEvidenceVariants": len(bounded),
                       "prunedEvidenceVariants": len(full) - len(bounded), "fullDistinctContextKeys": len(raw_ids),
                       "boundedDistinctContextKeys": len(bounded_ids), "prunedDistinctContextKeys": len(raw_ids - bounded_ids),
                       "baselineOwnedPrimaryKeys": sum(owners.get(k) == "primary" for k in raw_ids),
                       "baselineOwnedAlternateKeys": sum(owners.get(k) == "alternate" for k in raw_ids),
                       "fullUnownedContextKeys": len(raw_ids - set(owners)),
                       "baselineFreeChooserDistinctContexts": available_count,
                       "fullEvidenceSha256": digest(full), "boundedEvidenceSha256": digest(bounded),
                       "reason": "free-full-evidence-omitted-by-retention" if baseline_free else "full-evidence-all-owned"})
        # Constructive lower bound only: existing memberships/alternates stay exact.
        unused = [c for c in full if str(c.get("contextId") or c.get("candidateId")) not in used]
        chosen, _ = choose_contexts(unused, 3, policy["nearDuplicateJaccard"], diverse=True, lang=lang)
        keys = [str(c.get("contextId") or c.get("candidateId")) for c in chosen]
        if len(chosen) != 3 or len(set(keys)) != 3 or len(selected_rows) + len(witnesses) >= policy["maxTargetsPerLevel"]:
            continue
        require(not set(keys) & used and not any(near_duplicate(a["context"], b["context"], policy["nearDuplicateJaccard"])
                                                for i, a in enumerate(chosen) for b in chosen[i + 1:]),
                "invalid-strict-three-context-witness")
        witnesses.append({"targetId": target["targetId"], "freqRank": target["freqRank"], "level": target["level"],
                          "contextIds": keys, "candidateIds": [c["candidateId"] for c in chosen],
                          "evidenceSha256": digest(chosen), "addedContexts": 3})
        used.update(keys)
    require(digest(selected_rows) == before, "baseline-assignments-mutated")
    require(len(used) == len(owners) + 3 * len(witnesses), "witness-context-resource-count-mismatch")
    metadata = {**dict(graph_counts), "baselineAssignmentsSha256": before, "baselineTargets": len(selected_rows),
                "ownershipSnapshot": "complete-baseline-allocation",
                "baselinePrimaryReservations": sum(v == "primary" for v in owners.values()),
                "baselineAlternateReservations": sum(v == "alternate" for v in owners.values()),
                "lostTargets": len(lost_targets), "lossReasons": dict(Counter(x["reason"] for x in losses)),
                "baselineFreeChooserContextHistogram": dict(sorted(available_histogram.items())),
                "strictThreeContextAdditions": len(witnesses), "constructiveTargetTotal": len(selected_rows) + len(witnesses),
                "baselineAssignmentsPreserved": True, "zeroAdditionIsInconclusive": True,
                "witnessIsMaximum": False, "newMembershipsEachHaveThreeContexts": True}
    return metadata, losses, witnesses


def validate_full_targets(targets, lang, guard):
    for target in targets:
        require(target["targetId"] == target_id(lang, target["text"]) and core.level_of(target["freqRank"]) == target["level"],
                "full-target-identity-or-level-mismatch")
        require(target["freqRank"] > 60 and len(target["text"]) >= core.minimum_phrase(lang) and
                utf16_length(target["text"]) <= core.MAX_PHRASE, "full-target-quality-contract-mismatch")
        for c in target["contexts"]:
            require(target["sourceFamilies"] == c["sourceFamilies"] == ["tatoeba"] and
                    len(c["context"]) >= (6 if lang in core.CJK else core.MIN_SENTENCE) and
                    utf16_length(c["context"]) <= core.MAX_SENTENCE and c["meaning"].strip() and
                    utf16_length(c["meaning"]) <= core.MAX_TRANSLATION, "full-context-quality-contract-mismatch")
            matches = [s for s in word_spans(c["context"], lang) if canonical_target(s.surface) == canonical_target(target["text"])]
            require(len(matches) == 1, "full-edge-canonical-boundary-mismatch")
            span = matches[0]
            require(utf16_slice(c["context"], utf16_offset(c["context"], span.start), utf16_offset(c["context"], span.end)) == span.surface,
                    "full-edge-utf16-mismatch")
            if lang == "ja":
                kept, rejected, _ = guard.filter(c["context"], [(target["freqRank"], span.surface, target["targetId"], target["level"])])
                require(len(kept) == 1 and not rejected, "full-edge-japanese-boundary-mismatch")


def run(args):
    inputs, outputs = validate_paths(args)
    scratch, out = outputs
    scratch.mkdir(parents=True); out.mkdir(parents=True)
    step = "input-verification"
    guard = None
    try:
        initial = file_bindings(inputs)
        report = read_json(args.selection_report)
        source_pins = verify_source_pins(report)
        scope = scope_from_report(report)
        # Existing admission/source/pool validation is retained in full. Sanitize
        # exceptions at this task boundary, never echo parser source excerpts.
        step = "full-admitted-pool-validation"
        pool_report, identity = admitted.validate_pool(args)
        step = "selection-report-lineage-validation"
        verify_report(report, pool_report, identity)
        step = "quality-runtime-validation"
        runtime, guard = verify_runtime(report)
        manifest = {"status": "started", "publicationSafe": False, "inputs": initial,
                    "selectionSourcePins": source_pins, "diagnosticSourceSha256": sha256(Path(__file__)),
                    "runtime": runtime, "scope": {"deckIds": sorted(scope["decks"]), "pairs": scope["pairs"],
                    "rankLanguages": scope["languages"], "expectedCandidateRows": sum(scope["pairRows"].values())},
                    "policy": {k: report["policy"][k] for k in ("maxTargetsPerLevel", "contextsPerTarget", "nearDuplicateJaccard", "functionTop")},
                    "evidenceContextLimit": EVIDENCE_LIMIT, "exactHistoricalAssignmentsCompared": False}
        write_json(out / "INPUTS.json", manifest)
        step = "global-context-and-scoped-candidate-staging"
        global_path, subset_path = scratch / "global-contexts.sqlite3", scratch / "scoped-candidates.sqlite3"
        stage = stage_scoped(args.pool, global_path, subset_path, scope["pairs"], scope["languages"])
        require(stage["inputCandidates"] == identity["uniqueCandidates"] and stage["retainedCandidateRows"] == 33515,
                "staged-input-or-pair-count-mismatch")
        rank_evidence, analysis_counts, decks, losses, witnesses = {}, Counter(), [], [], []
        checked_decks = 0
        with closing(sqlite3.connect(global_path)) as global_db, closing(sqlite3.connect(subset_path)) as subset:
            for lang in scope["languages"]:
                step = "global-ranks-and-scoped-analysis"
                ranks = selection.build_ranks(global_db, "everyday", lang)
                rank_evidence[lang] = {"contexts": global_db.execute("SELECT COUNT(*) FROM context WHERE collection='everyday' AND lang=?", (lang,)).fetchone()[0],
                                      "forms": len(ranks), "orderedRanksSha256": digest(list(ranks.items()))}
                # Suppress the existing analyzer's progress/error text. A fresh
                # per-context analysis over this subset receives the full ranks.
                with redirect_stderr(io.StringIO()):
                    analysis_counts.update(selection.build_context_analysis(subset, "everyday", lang, ranks,
                                            report["policy"]["functionTop"], japanese_guard=guard if lang == "ja" else None,
                                            canonical_unique=True))
                del ranks
                for (choices_json,) in subset.execute("SELECT choices_json FROM context_analysis WHERE collection='everyday' AND lang=?", (lang,)):
                    choices = json.loads(choices_json)
                    require(len({c[2] for c in choices}) == len(choices), "duplicate-target-choice-in-context-analysis")
                for a, b in scope["pairs"]:
                    if a != lang:
                        continue
                    step = "bounded-baseline-and-full-evidence"
                    expected_rows = scope["pairRows"][a + "-" + b]
                    actual_rows = subset.execute("SELECT COUNT(*) FROM candidate WHERE collection='everyday' AND lang=? AND meaning_lang=?", (a, b)).fetchone()[0]
                    require(actual_rows == expected_rows, "scoped-pair-row-count-mismatch")
                    bounded, pair_stats = selection.pair_evidence(subset, "everyday", a, b, None, EVIDENCE_LIMIT, diverse=True)
                    full, full_stats = selection.pair_evidence(subset, "everyday", a, b, None, max(1, actual_rows), diverse=True)
                    require(pair_stats == full_stats and len(bounded) == len(full), "full-versus-bounded-counter-mismatch")
                    require([{k: v for k, v in t.items() if k != "contexts"} for t in bounded] ==
                            [{k: v for k, v in t.items() if k != "contexts"} for t in full], "full-versus-bounded-target-metadata-mismatch")
                    validate_full_targets(full, a, guard)
                    graph_path = scratch / f"graph-{a}-{b}.sqlite3"
                    with closing(sqlite3.connect(graph_path)) as graph:
                        persist_full_graph(graph, full)
                        del full
                        for level in core.LEVELS:
                            level_targets = [t for t in bounded if t["level"] == level]
                            chosen, stats, lost, _ = allocate_level(level_targets, a, report["policy"])
                            deck_id = f"{a}-{b}-everyday-{level}"
                            compare_baseline(scope["inventory"][deck_id], level_targets, stats, pair_stats)
                            checked_decks += 1
                            if deck_id not in scope["decks"]:
                                continue
                            metadata, lost_rows, new_rows = audit_deck(level_targets, chosen, lost, graph, a, report["policy"])
                            decks.append({"deckId": deck_id, "baselineDecision": scope["inventory"][deck_id]["decision"],
                                          **stats, **metadata, "strictThreeContextWitnesses": new_rows})
                            losses.extend({"deckId": deck_id, **row} for row in lost_rows)
                            witnesses.extend({"deckId": deck_id, **row} for row in new_rows)
        step = "final-integrity-and-metadata-output"
        require(file_bindings(inputs) == initial, "input-changed-during-diagnostic")
        require(verify_source_pins(report) == source_pins and sha256(Path(__file__)) == manifest["diagnosticSourceSha256"],
                "source-changed-during-diagnostic")
        require(prepare(report["admittedInput"]["learn"]) == runtime["segmentation"] and guard.evidence == runtime["japaneseBoundary"],
                "quality-runtime-changed-during-diagnostic")
        require(len(decks) == 20 and checked_decks == 39, "incomplete-diagnostic-deck-scope")
        write_json(out / "DECKS.json", sorted(decks, key=lambda d: d["deckId"]))
        with (out / "LOSSES.jsonl").open("xb") as handle:
            for row in losses:
                handle.write(encoded(row) + b"\n")
        summary = {"status": "complete-allocation-evidence-diagnostic", "publicationSafe": False,
                   "productionPolicyChanged": False, "semanticAccuracyCertified": False, "stage": stage,
                   "globalRanks": rank_evidence, "scopedContextAnalysisCounts": dict(analysis_counts),
                   "baselineReportCountsAndPairStatsEqual": True, "baselineCheckedDecks": checked_decks,
                   "diagnosticDecks": len(decks), "directions": len(scope["pairs"]),
                   "exactHistoricalAssignmentsCompared": False, "eligibleTargets": sum(d["eligibleTargets"] for d in decks),
                   "baselineSelectedTargets": sum(d["selectedTargets"] for d in decks), "collisionLosses": len(losses),
                   "lossReasons": dict(Counter(row["reason"] for row in losses)),
                   "strictThreeContextAdditions": len(witnesses), "witnessIsLowerBound": True,
                   "zeroAdditionIsInconclusive": True, "baselineAssignmentsPreserved": True,
                   "outputs": {p.name: {"sha256": sha256(p), "sizeBytes": p.stat().st_size} for p in sorted(out.iterdir()) if p.is_file()},
                   "limitations": ["Ranks use complete admitted-pool context evidence for the eight learning languages, not the selected pair subset.",
                       "Full graph covers all qualifying rows in the thirteen scoped pairs, not other pairs or unknown upstream exports.",
                       "Historical report counts are checked exactly; no historical complete selected payload was supplied for an assignment comparison.",
                       "Scoped analysis counters are not the historical full-pool quality-counter totals.",
                       "The append-only witness preserves every baseline membership/context and adds exactly three contexts per new membership.",
                       "Zero additions do not prove an optimum or unavoidable loss; no reassignment or primary-only matching is attempted.",
                       "Existing structural/source/domain gates do not certify translation meaning or all pedagogical quality.",
                       "Scratch databases contain corpus evidence and full rank words; they must never be packaged with these metadata outputs."]}
        write_json(out / "SUMMARY.json", summary)
        write_json(out / "STATUS.json", {"status": summary["status"], "complete": True, "publicationSafe": False,
                                         "summarySha256": sha256(out / "SUMMARY.json")})
        return summary
    except BaseException as error:
        code = error.code if isinstance(error, DiagnosticError) else "diagnostic-interrupted" if isinstance(error, KeyboardInterrupt) else "diagnostic-failed"
        failure = {"status": "failed-allocation-evidence-diagnostic", "publicationSafe": False,
                   "stage": step, "reasonCode": code, "errorType": type(error).__name__, "complete": False}
        if not (out / "STATUS.json").exists():
            write_json(out / "STATUS.json", failure)
        raise DiagnosticError(code) from None
    finally:
        if guard is not None:
            guard.close()


def parser():
    ap = argparse.ArgumentParser(description=__doc__)
    for key in ("pool", "pool-report", "selection-report", "scratch", "out"):
        ap.add_argument("--" + key, required=True)
    ap.add_argument("--registry", default=str(DEFAULT_REGISTRY))
    return ap


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        summary = run(args)
    except BaseException as error:
        code = error.code if isinstance(error, DiagnosticError) else "diagnostic-failed"
        print("Allocation diagnostic failed: " + code, file=sys.stderr)
        return 1
    print("Allocation diagnostic complete: " + str(summary["diagnosticDecks"]) + " decks; metadata only.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
