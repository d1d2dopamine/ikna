#!/usr/bin/env python3
"""Synthetic-only checks for the metadata-only allocation diagnostic.

No downloads, saved corpus execution, selected material export or publication.
The reference allocation is the unchanged production build_report loop, fed
small synthetic pair evidence, rather than a second hand-written allocator.
"""
from __future__ import annotations

from collections import Counter
from contextlib import redirect_stderr, redirect_stdout
from copy import deepcopy
import io
import json
from pathlib import Path
import random
import sqlite3
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import allocation_diagnostic as diagnostic
from ingest.model import Candidate, Origin, write_jsonl
import selection_experiment as selection
import selection_policy as policy_module


POLICY = {
    "maxTargetsPerLevel": 8000,
    "contextsPerTarget": 3,
    "nearDuplicateJaccard": .85,
}
STAT_KEYS = (
    "morphologyDeferred", "nearDuplicateContextsRejected",
    "contextCollisionTargetsRejected", "budgetRejected",
)


def evidence(ref, text, *, score=None, families=("tatoeba",), tokens=10):
    return {
        "candidateId": "candidate:" + ref,
        "contextId": "context:" + ref,
        "meaningId": "meaning:" + ref,
        "context": text,
        "meaning": "Synthetic translated fixture " + ref + ".",
        "sourceFamilies": list(families),
        "origins": [
            {"sourceFamily": family, "sourceVersion": "synthetic-only",
             "contextRef": "context:" + ref, "meaningRef": "meaning:" + ref}
            for family in families
        ],
        "alignmentScore": score,
        "tokenCount": tokens,
    }


def target(ref, rank, contexts, *, lemma=None, level="beginner", occurrences=None):
    return {
        "targetId": "target:" + ref,
        "text": "syntheticword" + ref,
        "freqRank": rank,
        "level": level,
        "lemmaKey": lemma or "target:" + ref,
        "lemmaSource": "fixture" if lemma else "unavailable",
        "evidenceContexts": len(contexts) if occurrences is None else occurrences,
        "sourceFamilies": sorted({f for row in contexts for f in row["sourceFamilies"]}),
        "contexts": contexts,
    }


def candidate(ref, text, meaning="es", *, lang="en", collection="everyday"):
    return Candidate(
        collection, lang, meaning, text, "Synthetic translation " + ref + ".",
        [Origin("tatoeba", "synthetic-only", "context:" + ref, "meaning:" + ref)],
    )


class SyntheticCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="ikna-allocation-fixture-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def production_allocation(self, targets, policy):
        """Run the real unchanged production allocation loop on fixture evidence."""
        db_path = self.root / "production-reference.sqlite3"
        if db_path.exists():
            db_path.unlink()
        with sqlite3.connect(db_path) as db:
            db.executescript(selection.SCHEMA)
            db.execute("INSERT INTO candidate VALUES(?,?,?,?,?,?,?,?,?,?)", (
                "synthetic-pair-marker", "everyday", "en", "es", "marker",
                "Synthetic pair marker.", "Synthetic translation.", "[]", None, "marker"))
        db.close()
        args = selection.parser().parse_args([
            "--candidates", str(self.root / "unused-synthetic-input.jsonl"),
            "--staging", str(db_path), "--json", str(self.root / "unused.json"),
            "--markdown", str(self.root / "unused.md"),
            "--preview", str(self.root / "unused-preview.jsonl"),
            "--learn", "en", "--meanings", "es", "--function-top", "0",
            "--quality-policy", "boundary-diversity-v2", "--min-deck", "1",
            "--thin-deck", "2", "--max-deck", str(policy["maxTargetsPerLevel"]),
            "--contexts-per-target", str(policy["contextsPerTarget"]),
            "--near-duplicate-threshold", str(policy["nearDuplicateJaccard"]),
        ])
        pair_stats = Counter(candidateRows=1, eligibleExactTargets=len(targets),
                             eligibleTargetOccurrences=sum(t["evidenceContexts"] for t in targets))
        with patch.object(selection, "stage", return_value={"uniqueCandidates": 1}), \
                patch.object(selection, "build_ranks", return_value={}), \
                patch.object(selection, "build_context_analysis", return_value={}), \
                patch.object(selection, "pair_evidence", return_value=(targets, pair_stats)), \
                redirect_stderr(io.StringIO()):
            report, rows = selection.build_report(args)
        deck = next(row for row in report["decks"] if row["level"] == "beginner")
        metadata = {"deckId", "collection", "lang", "meaningLang", "level"}
        selected = [{key: value for key, value in row.items() if key not in metadata}
                    for row in rows if row["level"] == "beginner"]
        return selected, {key: deck[key] for key in STAT_KEYS}, deck, dict(pair_stats)

    def assert_metadata_only(self, payload, targets):
        serialized = json.dumps(payload, ensure_ascii=False, sort_keys=True)
        for item in targets:
            self.assertNotIn(item["text"], serialized)
            for row in item["contexts"]:
                self.assertNotIn(row["context"], serialized)
                self.assertNotIn(row["meaning"], serialized)
        forbidden = {"context", "meaning", "text", "origins", "choices_json", "origins_json"}

        def inspect(value):
            if isinstance(value, dict):
                self.assertFalse(forbidden.intersection(value), value.keys())
                for child in value.values():
                    inspect(child)
            elif isinstance(value, (list, tuple)):
                for child in value:
                    inspect(child)
        inspect(payload)


class ScopedStagingTests(SyntheticCase):
    def stage_fixture(self, batch_size):
        # The representative precedes the retained pair. Keeping only en-es
        # must not change the source spelling chosen for context analysis.
        records = [
            candidate("outside-first", "FIRSTword  Alternateword calmword.", "de"),
            candidate("inside-variant", "firstword alternateword CALMword.", "es"),
            candidate("inside-other", "Visibleword novelword distantword.", "es"),
            candidate("outside-rank-a", "Alternateword alternateword rankingword.", "fr"),
            candidate("outside-rank-b", "Alternateword additionalword elsewhereword.", "it"),
            candidate("other-learning", "Unrelatedword spareword languageword.", "en", lang="fr"),
        ]
        pool = self.root / "synthetic.jsonl.gz"
        write_jsonl(str(pool), records)
        full_path = self.root / "full.sqlite3"
        global_path = self.root / "global.sqlite3"
        subset_path = self.root / "subset.sqlite3"
        selection.stage([str(pool)], str(full_path), batch_size=batch_size)
        stats = diagnostic.stage_scoped(
            pool, global_path, subset_path, [("en", "es")], ["en"], batch_size=batch_size)
        return records, stats, full_path, global_path, subset_path

    def test_context_dedup_first_representative_and_tie_order_match_full_stage(self):
        records, _stats, full_path, global_path, subset_path = self.stage_fixture(1)
        with sqlite3.connect(full_path) as full, sqlite3.connect(global_path) as global_db, \
                sqlite3.connect(subset_path) as subset:
            query = "SELECT collection,lang,context_key,context FROM context WHERE lang='en' ORDER BY context_key"
            self.assertEqual(full.execute(query).fetchall(), global_db.execute(query).fetchall())
            self.assertEqual([("en",)], global_db.execute("SELECT DISTINCT lang FROM context").fetchall())
            expected = full.execute(
                "SELECT x.* FROM context x WHERE x.context_key IN "
                "(SELECT context_key FROM candidate WHERE lang='en' AND meaning_lang='es') "
                "AND x.lang='en' ORDER BY x.context_key").fetchall()
            self.assertEqual(expected, subset.execute(query).fetchall())
            first_key = selection._context_key("everyday", "en", records[0].context)
            representative = subset.execute("SELECT context FROM context WHERE context_key=?", (first_key,)).fetchone()[0]
            self.assertEqual(records[0].context, representative)
            ranks = selection.build_ranks(full, "everyday", "en")
            self.assertEqual(ranks, selection.build_ranks(global_db, "everyday", "en"))
            self.assertNotEqual(ranks, selection.build_ranks(subset, "everyday", "en"))
            counts = Counter()
            for (text,) in full.execute("SELECT context FROM context WHERE lang='en' ORDER BY context_key"):
                counts.update(word.lower() for word in selection.core.words(text, "en"))
            # Counter's equal-frequency order is the production first-seen
            # order, not alphabetical order or candidate-stream order.
            self.assertEqual(ranks, {word: i for i, (word, _n) in enumerate(counts.most_common(), 1)})
            self.assertEqual(4, counts["alternateword"])
            self.assertEqual(2, subset.execute("SELECT COUNT(*) FROM candidate").fetchone()[0])

    def test_scoped_analysis_and_pair_evidence_are_exact_with_global_ranks(self):
        _records, _stats, full_path, global_path, subset_path = self.stage_fixture(2)
        with sqlite3.connect(full_path) as full, sqlite3.connect(global_path) as global_db, \
                sqlite3.connect(subset_path) as subset:
            ranks = selection.build_ranks(global_db, "everyday", "en")
            full_ranks = selection.build_ranks(full, "everyday", "en")
            selection.build_context_analysis(full, "everyday", "en", full_ranks, 0, canonical_unique=True)
            selection.build_context_analysis(subset, "everyday", "en", ranks, 0, canonical_unique=True)
            query = "SELECT * FROM context_analysis WHERE analysis_key IN (SELECT analysis_key FROM candidate WHERE meaning_lang='es') ORDER BY analysis_key"
            self.assertEqual(full.execute(query).fetchall(), subset.execute(query).fetchall())
            full_targets, full_stats = selection.pair_evidence(full, "everyday", "en", "es", None, 12, diverse=True)
            subset_targets, subset_stats = selection.pair_evidence(subset, "everyday", "en", "es", None, 12, diverse=True)
            self.assertEqual(full_targets, subset_targets)
            self.assertEqual(full_stats, subset_stats)
            self.assertTrue(any(t["text"] == "FIRSTword" for t in subset_targets))
            self.assertTrue(any(c["context"] == "firstword alternateword CALMword."
                                for t in subset_targets for c in t["contexts"]))

    def test_existing_staging_or_input_alias_is_rejected_without_overwrite(self):
        pool = self.root / "source.jsonl"
        write_jsonl(str(pool), [candidate("one", "Synthetic complete fixture sentence.")])
        original = pool.read_bytes()
        for global_name, subset_name in (("source.jsonl", "new.sqlite3"),
                                          ("new.sqlite3", "source.jsonl"),
                                          ("same.sqlite3", "same.sqlite3")):
            with self.subTest(global_name=global_name, subset_name=subset_name):
                with self.assertRaises((ValueError, FileExistsError)):
                    diagnostic.stage_scoped(pool, self.root / global_name, self.root / subset_name,
                                            [("en", "es")], ["en"])
                self.assertEqual(original, pool.read_bytes())

    def test_duplicate_candidate_input_is_rejected_for_the_zero_duplicate_pool(self):
        pool = self.root / "repeated.jsonl"
        record = candidate("repeat", "Synthetic repeated fixture sentence.")
        write_jsonl(str(pool), [record, record])
        with self.assertRaisesRegex(diagnostic.DiagnosticError, "duplicate-retained-candidate-identity"):
            diagnostic.stage_scoped(pool, self.root / "global.sqlite3", self.root / "subset.sqlite3",
                                    [("en", "es")], ["en"], batch_size=1)


class AllocationParityTests(SyntheticCase):
    def test_exact_production_rows_order_counters_and_unmodified_evidence(self):
        one = evidence("one", "Alpha beta gamma delta epsilon zeta.", score=4)
        two = evidence("two", "A meadow blooms beside the silent river.", score=3)
        near = evidence("near", "Alpha beta gamma delta epsilon zeta!", score=2)
        three = evidence("three", "Distant planets revolve beyond our galaxy.", score=1)
        last = evidence("last", "A curious turtle carries a polished shell.")
        targets = [target("first", 1, [one, two, near, three], lemma="shared-lemma"),
                   target("deferred", 2, [last], lemma="shared-lemma"),
                   target("collision", 3, [one, two, three]),
                   target("fresh", 4, [last]),
                   target("budget", 5, [evidence("budget", "Clouds drift above a tiny village.")])]
        original = deepcopy(targets)
        for budget in (1, 2, 3, 8000):
            with self.subTest(budget=budget):
                policy = {**POLICY, "maxTargetsPerLevel": budget}
                expected, expected_stats, _deck, _pair_stats = self.production_allocation(targets, policy)
                selected, stats, _lost, _owners = diagnostic.allocate_level(targets, "en", policy)
                self.assertEqual(expected, selected)
                self.assertEqual(expected_stats, {key: stats[key] for key in STAT_KEYS})
                self.assertEqual(original, targets)
        self.assertGreater(expected_stats["morphologyDeferred"], 0)
        self.assertGreater(expected_stats["nearDuplicateContextsRejected"], 0)
        self.assertGreater(expected_stats["contextCollisionTargetsRejected"], 0)

    def test_seeded_allocation_differential_with_collisions_and_lemma_deferrals(self):
        rng = random.Random(361012)
        for case in range(24):
            contexts = [evidence(
                f"case{case}-context{i}",
                rng.choice(("Amber birches shelter the winding path.",
                            "Violet planets circle a distant star.",
                            "Quiet turtles cross the shallow stream.",
                            "Crimson lanterns light an empty terrace.")) + " " + chr(65 + i),
                score=rng.choice((None, .5, 1.0)),
                families=rng.choice((("tatoeba",), ("tatoeba", "synthetic-evidence"))),
                tokens=rng.randrange(4, 13)) for i in range(9)]
            targets = [target(f"case{case}-target{i}", rng.randrange(1, 5),
                              rng.sample(contexts, rng.randrange(1, 7)),
                              lemma="lemma:" + str(rng.randrange(4))) for i in range(8)]
            rng.shuffle(targets)
            policy = {**POLICY, "maxTargetsPerLevel": rng.randrange(1, 9)}
            original = deepcopy(targets)
            with self.subTest(case=case):
                expected, expected_stats, _deck, _pair_stats = self.production_allocation(targets, policy)
                actual, stats, _lost, _owners = diagnostic.allocate_level(targets, "en", policy)
                self.assertEqual(expected, actual)
                self.assertEqual(expected_stats, {key: stats[key] for key in STAT_KEYS})
                self.assertEqual(original, targets)

    def test_empty_level_matches_production(self):
        expected, expected_stats, _deck, _pair_stats = self.production_allocation([], POLICY)
        actual, stats, lost, owners = diagnostic.allocate_level([], "en", POLICY)
        self.assertEqual(expected, actual)
        self.assertEqual(expected_stats, {key: stats[key] for key in STAT_KEYS})
        self.assertFalse(lost)
        self.assertFalse(owners)


class FullEvidenceReferenceTests(SyntheticCase):
    def test_independent_unbounded_pair_evidence_contains_pruned_occurrences(self):
        # Production's ordinary limit=12 pruning starts after 36 evidence rows.
        # Raising the limit to the verified candidate-row count prevents every
        # pruning call only when each analysis row has unique target identities.
        with sqlite3.connect(":memory:") as db:
            db.executescript(selection.SCHEMA)
            for index in range(40):
                ref = f"synthetic-{index:02d}"
                text = f"Fixture sentinel marker{index:02d} carries an entirely synthetic sentence."
                context = evidence(ref, text)
                context_key = selection._context_key("everyday", "en", text)
                db.execute("INSERT INTO candidate VALUES(?,?,?,?,?,?,?,?,?,?)", (
                    context["candidateId"], "everyday", "en", "es", context_key,
                    text, context["meaning"], json.dumps(context["origins"]), None, context_key))
                db.execute("INSERT INTO context VALUES(?,?,?,?)", ("everyday", "en", context_key, text))
                db.execute("INSERT INTO context_analysis VALUES(?,?,?,?,?)", (
                    "everyday", "en", context_key, 10,
                    json.dumps([[1, "sentinel", "target:full-evidence", "beginner"]])))
            db.commit()
            bounded, bounded_stats = selection.pair_evidence(db, "everyday", "en", "es", None, 12, diverse=True)
            with patch.object(selection, "choose_contexts", wraps=selection.choose_contexts) as chooser:
                full, full_stats = selection.pair_evidence(db, "everyday", "en", "es", None, 40, diverse=True)
            chooser.assert_not_called()
            self.assertEqual(full_stats, bounded_stats)
            self.assertEqual(40, full_stats["candidateRows"])
            self.assertEqual(40, full[0]["evidenceContexts"])
            self.assertEqual(40, len(full[0]["contexts"]))
            self.assertLess(len(bounded[0]["contexts"]), 40)
            self.assertEqual(
                {row[0] for row in db.execute("SELECT id FROM candidate")},
                {row["candidateId"] for row in full[0]["contexts"]},
            )

    def test_full_edge_identity_unique_boundary_and_utf16_validation(self):
        sentence = "Synthetic 😀 lanternword lights an empty courtyard."
        tid = selection.target_id("en", "lanternword")
        item = target("temporary", 61, [evidence("span", sentence)])
        item.update(targetId=tid, text="lanternword", level=selection.core.level_of(61))
        diagnostic.validate_full_targets([item], "en", None)
        for fault in ("target-identity", "level", "repeated-token", "partial-token"):
            changed = deepcopy(item)
            if fault == "target-identity":
                changed["targetId"] = "target:invalid"
            elif fault == "level":
                changed["level"] = "advanced"
            elif fault == "repeated-token":
                changed["contexts"][0]["context"] += " LANTERNWORD."
            else:
                changed["contexts"][0]["context"] = sentence.replace("lanternword", "extral anternword")
            with self.subTest(fault=fault), self.assertRaises(diagnostic.DiagnosticError):
                diagnostic.validate_full_targets([changed], "en", None)


class FullGraphAuditTests(SyntheticCase):
    def audit_fixture(self):
        texts = (
            "Amber birches shelter a winding path.",
            "Violet planets orbit beyond distant stars.",
            "Quiet turtles cross a shallow stream.",
            "Crimson lanterns illuminate an empty terrace.",
            "Silver cranes travel across autumn marshes.",
            "Golden oranges rest inside wicker baskets.",
            "Bronze bells echo through mountain valleys.",
            "Indigo sailboats drift beside rocky islands.",
        )
        contexts = [evidence(str(i + 1), text, score=20 - i) for i, text in enumerate(texts)]
        c1, c2, c3, c4, c5, c6, c7, c8 = contexts
        full = [target("a", 1, [c1, c2, c3]),
                target("b", 2, [c1, c2, c3, c4, c5, c6]),
                target("c", 3, [c2, c7, c8]),
                target("d", 4, [c3, c4, c5, c6]),
                target("e", 5, [c1, c2, c3])]
        bounded = deepcopy(full)
        for item, kept in zip(bounded, ((c1, c2, c3), (c1, c2), (c2,), (c3,), (c1,))):
            item["contexts"] = deepcopy(list(kept))
        return full, bounded

    def test_full_graph_pruning_primary_and_alternate_ownership_are_separate(self):
        full, bounded = self.audit_fixture()
        original = deepcopy((full, bounded))
        selected, stats, lost, owners = diagnostic.allocate_level(bounded, "en", POLICY)
        self.assertEqual(1, len(selected))
        self.assertEqual(4, stats["contextCollisionTargetsRejected"])
        self.assertEqual("primary", owners["context:1"]["role"])
        self.assertEqual("alternate", owners["context:2"]["role"])
        self.assertEqual("alternate", owners["context:3"]["role"])
        before = deepcopy(selected)
        with sqlite3.connect(":memory:") as graph:
            self.assertEqual(19, diagnostic.persist_full_graph(graph, full))
            self.assertEqual(full[1]["contexts"], diagnostic.full_contexts(graph, "target:b"))
            metadata, losses, witnesses = diagnostic.audit_deck(bounded, selected, lost, graph, "en", POLICY)
        self.assertEqual(original, (full, bounded))
        self.assertEqual(before, selected)
        self.assertEqual(19, metadata["fullEvidenceVariants"])
        self.assertEqual(8, metadata["boundedEvidenceVariants"])
        self.assertEqual(11, metadata["prunedEvidenceVariants"])
        self.assertEqual(1, metadata["baselinePrimaryReservations"])
        self.assertEqual(2, metadata["baselineAlternateReservations"])
        self.assertEqual({"0": 1, "2": 1, "3": 2}, metadata["baselineFreeChooserContextHistogram"])
        self.assertEqual({"free-full-evidence-omitted-by-retention": 3, "full-evidence-all-owned": 1},
                         metadata["lossReasons"])
        rows = {row["targetId"]: row for row in losses}
        b = rows["target:b"]
        for key, expected in {
            "fullEvidenceVariants": 6, "boundedEvidenceVariants": 2,
            "prunedEvidenceVariants": 4, "fullDistinctContextKeys": 6,
            "boundedDistinctContextKeys": 2, "prunedDistinctContextKeys": 4,
            "baselineOwnedPrimaryKeys": 1, "baselineOwnedAlternateKeys": 2,
            "fullUnownedContextKeys": 3, "baselineFreeChooserDistinctContexts": 3,
        }.items():
            self.assertEqual(expected, b[key], key)
        self.assertEqual(0, rows["target:c"]["baselineOwnedPrimaryKeys"])
        self.assertEqual(1, rows["target:c"]["baselineOwnedAlternateKeys"])
        self.assertEqual("full-evidence-all-owned", rows["target:e"]["reason"])
        self.assert_metadata_only((metadata, losses, witnesses), full)

    def test_witness_is_strict_three_append_only_and_never_reuses_reservations(self):
        full, bounded = self.audit_fixture()
        selected, _stats, lost, _owners = diagnostic.allocate_level(bounded, "en", POLICY)
        before = deepcopy(selected)
        with sqlite3.connect(":memory:") as graph:
            diagnostic.persist_full_graph(graph, full)
            metadata, _losses, witnesses = diagnostic.audit_deck(bounded, selected, lost, graph, "en", POLICY)
        self.assertEqual(before, selected)
        self.assertTrue(metadata["baselineAssignmentsPreserved"])
        self.assertTrue(metadata["zeroAdditionIsInconclusive"])
        self.assertFalse(metadata["witnessIsMaximum"])
        self.assertEqual(2, metadata["constructiveTargetTotal"])
        self.assertEqual(1, metadata["strictThreeContextAdditions"])
        self.assertEqual(["target:b"], [row["targetId"] for row in witnesses])
        self.assertEqual(3, witnesses[0]["addedContexts"])
        # The expected witness comes from the unchanged public chooser over all
        # unowned raw evidence, preserving its exact candidate order and fields.
        used = {c["contextId"] for row in before for c in row["contexts"]}
        eligible = [c for c in full[1]["contexts"] if c["contextId"] not in used]
        expected, _ = policy_module.choose_contexts(eligible, 3, .85, diverse=True, lang="en")
        self.assertEqual([c["contextId"] for c in expected], witnesses[0]["contextIds"])
        self.assertEqual([c["candidateId"] for c in expected], witnesses[0]["candidateIds"])
        self.assertFalse(used.intersection(witnesses[0]["contextIds"]))
        self.assertEqual(3, len(set(witnesses[0]["contextIds"])))

    def test_three_rows_are_insufficient_when_ids_repeat_or_contexts_are_near_duplicates(self):
        reserved = evidence("reserved", "A copper violin rests against the wall.", score=10)
        free = [evidence("f1", "Amber birches shelter a winding path.", score=3),
                evidence("f2", "Violet planets orbit beyond distant stars.", score=2),
                evidence("f3", "Quiet turtles cross a shallow stream.", score=1)]
        for fault in ("duplicate-resource", "near-duplicate"):
            with self.subTest(fault=fault), sqlite3.connect(":memory:") as graph:
                rows = deepcopy(free)
                if fault == "duplicate-resource":
                    rows[1]["contextId"] = rows[0]["contextId"]
                else:
                    rows[1]["context"] = rows[0]["context"].replace(".", "!")
                full = [target("owner", 1, [reserved]), target("lost", 2, [reserved, *rows])]
                bounded = deepcopy(full)
                bounded[1]["contexts"] = [deepcopy(reserved)]
                selected, _stats, lost, _owners = diagnostic.allocate_level(bounded, "en", POLICY)
                diagnostic.persist_full_graph(graph, full)
                metadata, losses, witnesses = diagnostic.audit_deck(bounded, selected, lost, graph, "en", POLICY)
                self.assertEqual(0, metadata["strictThreeContextAdditions"])
                self.assertEqual(2, losses[0]["baselineFreeChooserDistinctContexts"])
                self.assertEqual([], witnesses)

    def test_incomplete_or_changed_raw_evidence_fails_closed(self):
        full, bounded = self.audit_fixture()
        with sqlite3.connect(":memory:") as graph:
            incomplete = deepcopy(full)
            incomplete[1]["contexts"].pop()
            with self.assertRaisesRegex(diagnostic.DiagnosticError, "unpruned-evidence-count-mismatch"):
                diagnostic.persist_full_graph(graph, incomplete)
        selected, _stats, lost, _owners = diagnostic.allocate_level(bounded, "en", POLICY)
        with sqlite3.connect(":memory:") as graph:
            diagnostic.persist_full_graph(graph, full)
            bounded[1]["contexts"][0]["meaning"] = "Changed synthetic private sentence."
            with self.assertRaisesRegex(diagnostic.DiagnosticError, "bounded-evidence-not-contained-in-full-graph"):
                diagnostic.audit_deck(bounded, selected, lost, graph, "en", POLICY)


    def test_duplicate_raw_target_candidate_edge_fails_closed(self):
        full, bounded = self.audit_fixture()
        full[1]["contexts"].append(deepcopy(full[1]["contexts"][0]))
        full[1]["evidenceContexts"] += 1
        selected, _stats, lost, _owners = diagnostic.allocate_level(bounded, "en", POLICY)
        with sqlite3.connect(":memory:") as graph:
            diagnostic.persist_full_graph(graph, full)
            with self.assertRaisesRegex(diagnostic.DiagnosticError, "duplicate-target-candidate-edge"):
                diagnostic.audit_deck(bounded, selected, lost, graph, "en", POLICY)


class MetadataGateTests(SyntheticCase):
    @staticmethod
    def scope_report():
        rows = []
        for level, eligible, selected in (("beginner", 1002, 999), ("middle", 15, 12),
                                          ("advanced", 2000, 1000)):
            rows.append({
                "deckId": "en-es-everyday-" + level, "collection": "everyday",
                "lang": "en", "meaningLang": "es", "level": level,
                "eligibleTargets": eligible, "selectedTargets": selected,
                "budgetRejected": 0, "contextCollisionTargetsRejected": eligible - selected,
                "nearDuplicateContextsRejected": 0, "morphologyDeferred": 0,
                "pairStats": {"candidateRows": 7, "eligibleExactTargets": 3017},
            })
        return {"decks": rows}

    @staticmethod
    def accepted_report():
        # Only metadata is synthetic here. No historical payload is read or
        # created; verify_report's exact historical identity gates remain active.
        identity = {"uniqueCandidates": 11099490, "learn": ["en"], "syntheticFixture": True}
        report = {
            "reportVersion": 1, "part": 10, "status": "admitted-everyday-selection-review",
            "publicationSafe": False, "morphologyUsed": False, "admittedInput": identity,
            "selectedMaterial": {"logicalSha256": diagnostic.HISTORICAL_LOGICAL},
            "stage": {"uniqueCandidates": 11099490, "duplicateCandidates": 0},
            "policy": {**POLICY, "minDeck": 40, "thinDeck": 1000, "functionTop": 60},
            "qualityPolicy": {"id": "boundary-diversity-v2", "version": 2,
                              "semanticAccuracyCertified": False,
                              "sourceQuarantine": {"sha256": diagnostic.sha256(
                                  diagnostic.HERE / "sources/selection-quarantine.json")}},
        }
        return report, {"sieve": {"functionTop": 60}}, identity

    def test_scope_retains_only_allocation_cells_but_checks_every_pair_level(self):
        report = self.scope_report()
        scope = diagnostic.scope_from_report(report, enforce_historical=False)
        self.assertEqual([("en", "es")], scope["pairs"])
        self.assertEqual(["en"], scope["languages"])
        self.assertEqual({"en-es": 7}, scope["pairRows"])
        self.assertEqual(["en-es-everyday-beginner"], list(scope["decks"]))
        self.assertEqual(3, len(scope["inventory"]))
        with self.assertRaisesRegex(diagnostic.DiagnosticError, "historical-diagnostic-scope-drift"):
            diagnostic.scope_from_report(report)

    def test_report_scope_missing_duplicated_inconsistent_or_invalid_counters_fail_closed(self):
        def mutations():
            yield "missing level", lambda rows: rows.pop()
            yield "duplicate deck", lambda rows: rows.append(deepcopy(rows[0]))
            yield "changed pair count", lambda rows: rows[1]["pairStats"].update(candidateRows=8)
            yield "negative counter", lambda rows: rows[0].update(selectedTargets=-1)
            yield "boolean counter", lambda rows: rows[0].update(budgetRejected=False)
            yield "budget loss", lambda rows: rows[0].update(budgetRejected=1)
            yield "wrong collection", lambda rows: rows[0].update(collection="knowledge")
            yield "wrong identity", lambda rows: rows[0].update(deckId="synthetic-wrong-deck")
        for label, mutate in mutations():
            with self.subTest(change=label):
                report = self.scope_report()
                mutate(report["decks"])
                with self.assertRaises(diagnostic.DiagnosticError):
                    diagnostic.scope_from_report(report, enforce_historical=False)

    def test_each_historical_report_and_policy_drift_fails_closed(self):
        report, pool_report, identity = self.accepted_report()
        diagnostic.verify_report(report, pool_report, identity)
        changes = [
            ("publicationSafe", True), ("morphologyUsed", True), ("part", 11),
            ("reportVersion", 2), ("status", "preview"),
            ("selectedMaterial", {"logicalSha256": "0" * 64}),
            ("admittedInput", {**identity, "uniqueCandidates": 1}),
            ("stage", {"uniqueCandidates": 11099490, "duplicateCandidates": 1}),
        ]
        for field, value in changes:
            with self.subTest(field=field):
                changed = deepcopy(report)
                changed[field] = value
                with self.assertRaises(diagnostic.DiagnosticError):
                    diagnostic.verify_report(changed, pool_report, identity)
        for field, value in {"maxTargetsPerLevel": 12000, "contextsPerTarget": 2,
                             "nearDuplicateJaccard": .8, "minDeck": 1,
                             "thinDeck": 999, "functionTop": 0}.items():
            with self.subTest(policy=field):
                changed = deepcopy(report)
                changed["policy"][field] = value
                with self.assertRaisesRegex(diagnostic.DiagnosticError, "historical-selection-policy-mismatch"):
                    diagnostic.verify_report(changed, pool_report, identity)
        for field, value in {"id": "legacy-v1", "version": 1,
                             "semanticAccuracyCertified": True,
                             "sourceQuarantine": {"sha256": "0" * 64}}.items():
            with self.subTest(quality=field):
                changed = deepcopy(report)
                changed["qualityPolicy"][field] = value
                with self.assertRaises(diagnostic.DiagnosticError):
                    diagnostic.verify_report(changed, pool_report, identity)
        with self.assertRaisesRegex(diagnostic.DiagnosticError, "function-rank-policy-mismatch"):
            diagnostic.verify_report(report, {"sieve": {"functionTop": 0}}, identity)

    def test_every_baseline_count_and_pair_stat_is_compared_exactly(self):
        targets = [target("a", 1, [evidence("a", "A synthetic amber lantern glows.")]),
                   target("b", 2, [evidence("a", "A synthetic amber lantern glows.")])]
        selected, stats, _lost, _owners = diagnostic.allocate_level(targets, "en", POLICY)
        pair_stats = {"candidateRows": 1, "eligibleTargetOccurrences": 2, "eligibleExactTargets": 2}
        expected = {**stats, "decision": "omit-below-minimum", "pairStats": pair_stats}
        self.assertEqual(1, len(selected))
        diagnostic.compare_baseline(expected, targets, stats, pair_stats)
        for field in stats:
            with self.subTest(counter=field):
                changed = deepcopy(expected)
                changed[field] += 1
                with self.assertRaises(diagnostic.DiagnosticError):
                    diagnostic.compare_baseline(changed, targets, stats, pair_stats)
        for field in pair_stats:
            with self.subTest(pair_counter=field):
                changed = deepcopy(expected)
                changed["pairStats"][field] += 1
                with self.assertRaisesRegex(diagnostic.DiagnosticError, "baseline-pair-counters-mismatch"):
                    diagnostic.compare_baseline(changed, targets, stats, pair_stats)
        changed = {**expected, "decision": "publish-thin"}
        with self.assertRaisesRegex(diagnostic.DiagnosticError, "baseline-decision-mismatch"):
            diagnostic.compare_baseline(changed, targets, stats, pair_stats)

    def test_source_manifest_missing_extra_and_changed_hashes_fail_closed(self):
        report = {"selectionPipelineSha256": {
            name: diagnostic.sha256(diagnostic.HERE / name) for name in diagnostic.SOURCE_FILES}}
        actual = diagnostic.verify_source_pins(report)
        self.assertEqual(report["selectionPipelineSha256"], actual)
        for fault in ("missing", "extra", "changed"):
            changed = deepcopy(report)
            pins = changed["selectionPipelineSha256"]
            if fault == "missing":
                pins.pop(diagnostic.SOURCE_FILES[0])
            elif fault == "extra":
                pins["unexpected-synthetic-source.py"] = "0" * 64
            else:
                pins[diagnostic.SOURCE_FILES[0]] = "0" * 64
            with self.subTest(fault=fault), self.assertRaises(diagnostic.DiagnosticError):
                diagnostic.verify_source_pins(changed)

    def test_input_binding_detects_same_length_byte_drift(self):
        path = self.root / "synthetic-input.bin"
        path.write_bytes(b"synthetic-A")
        before = diagnostic.file_bindings([path])
        path.write_bytes(b"synthetic-B")
        after = diagnostic.file_bindings([path])
        self.assertEqual(before[str(path)]["sizeBytes"], after[str(path)]["sizeBytes"])
        self.assertNotEqual(before[str(path)]["sha256"], after[str(path)]["sha256"])

    def test_cli_exposes_no_fixture_relaxed_gate_or_selection_policy_flags(self):
        options = {option for action in diagnostic.parser()._actions for option in action.option_strings}
        self.assertEqual({"-h", "--help", "--pool", "--pool-report", "--selection-report",
                          "--scratch", "--out", "--registry"}, options)

    def test_failure_metadata_and_cli_logs_never_echo_source_sentences(self):
        secret = "Synthetic private sentence that must never escape into diagnostic logs."
        paths = {}
        for name in ("pool", "pool-report", "selection-report", "registry"):
            path = self.root / (name + ".json")
            path.write_text("{}", encoding="utf-8")
            paths[name] = path
        argv = [part for name, path in paths.items() for part in ("--" + name, str(path))]
        argv += ["--scratch", str(self.root / "scratch"), "--out", str(self.root / "out")]
        stdout, stderr = io.StringIO(), io.StringIO()
        with patch.object(diagnostic, "verify_source_pins", return_value={}), \
                patch.object(diagnostic, "scope_from_report", return_value={}), \
                patch.object(diagnostic.admitted, "validate_pool", side_effect=ValueError(secret)), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            code = diagnostic.main(argv)
        self.assertEqual(1, code)
        self.assertNotIn(secret, stdout.getvalue() + stderr.getvalue())
        self.assertEqual({"STATUS.json"}, {path.name for path in (self.root / "out").iterdir()})
        status = json.loads((self.root / "out/STATUS.json").read_text())
        self.assertFalse(status["complete"])
        self.assertFalse(status["publicationSafe"])
        self.assertNotIn(secret, json.dumps(status))

    def test_invalid_candidate_text_is_not_exposed_by_staging_error(self):
        secret = "Synthetic private tokenword and a whole unpublished sentence."
        pool = self.root / "invalid.jsonl"
        pool.write_text(json.dumps({"candidateVersion": 1, "context": secret}) + "\n")
        stdout, stderr = io.StringIO(), io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            with self.assertRaisesRegex(diagnostic.DiagnosticError, "invalid-candidate-during-staging") as error:
                diagnostic.stage_scoped(pool, self.root / "global.db", self.root / "subset.db",
                                        [("en", "es")], ["en"])
        self.assertNotIn(secret, str(error.exception) + stdout.getvalue() + stderr.getvalue())

    def test_final_integrity_detects_input_report_and_source_changes_without_completion(self):
        # Isolate run's final integrity barrier. Fixture-only stage/scope/runtime
        # hooks bypass expensive work in this unit test, never the CLI. The run
        # is intentionally unable to succeed and must stop before scope finish.
        for fault in ("input", "report", "source"):
            with self.subTest(fault=fault):
                root = self.root / fault
                source_root = root / "synthetic-source"
                source_root.mkdir(parents=True)
                for name in diagnostic.SOURCE_FILES:
                    path = source_root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(b"synthetic source bytes\n")
                paths = {name: root / (name + ".json")
                         for name in ("pool", "pool-report", "selection-report", "registry")}
                for path in paths.values():
                    path.write_text("{}")
                with patch.object(diagnostic, "HERE", source_root):
                    report, pool_report, identity = self.accepted_report()
                    report["selectionPipelineSha256"] = {
                        name: diagnostic.sha256(source_root / name) for name in diagnostic.SOURCE_FILES}
                    paths["selection-report"].write_text(json.dumps(report))
                    argv = [part for name, path in paths.items() for part in ("--" + name, str(path))]
                    argv += ["--scratch", str(root / "scratch"), "--out", str(root / "out")]
                    args = diagnostic.parser().parse_args(argv)
                    closed = []
                    guard = SimpleNamespace(evidence={"engine": "synthetic-only"},
                                            close=lambda: closed.append(True))
                    runtime = {"segmentation": diagnostic.prepare(["en"]), "japaneseBoundary": guard.evidence}
                    scope = {"decks": {}, "inventory": {}, "pairs": [], "languages": [], "pairRows": {}}

                    def mutate_during_stage(*_args, **_kwargs):
                        if fault == "input":
                            paths["pool"].write_text("[]")
                        elif fault == "report":
                            paths["selection-report"].write_text(json.dumps(report) + "\n")
                        else:
                            (source_root / diagnostic.SOURCE_FILES[0]).write_bytes(b"changed source bytes\n")
                        return {"inputCandidates": 11099490, "retainedCandidateRows": 33515}

                    reason = ("selection-source-fingerprint-mismatch" if fault == "source"
                              else "input-changed-during-diagnostic")
                    with patch.object(diagnostic, "scope_from_report", return_value=scope), \
                            patch.object(diagnostic.admitted, "validate_pool", return_value=(pool_report, identity)), \
                            patch.object(diagnostic, "verify_runtime", return_value=(runtime, guard)), \
                            patch.object(diagnostic, "stage_scoped", side_effect=mutate_during_stage):
                        with self.assertRaisesRegex(diagnostic.DiagnosticError, reason):
                            diagnostic.run(args)
                self.assertEqual([True], closed)
                files = {path.name for path in (root / "out").iterdir()}
                self.assertEqual({"INPUTS.json", "STATUS.json"}, files)
                status = json.loads((root / "out/STATUS.json").read_text())
                self.assertFalse(status["complete"])
                self.assertEqual(reason, status["reasonCode"])

    def test_duplicate_analysis_target_identity_stops_before_pair_evidence(self):
        report, pool_report, identity = self.accepted_report()
        report["selectionPipelineSha256"] = {
            name: diagnostic.sha256(diagnostic.HERE / name) for name in diagnostic.SOURCE_FILES}
        paths = {name: self.root / (name + ".json")
                 for name in ("pool", "pool-report", "selection-report", "registry")}
        for path in paths.values():
            path.write_text("{}")
        paths["selection-report"].write_text(json.dumps(report))
        write_jsonl(str(paths["pool"]), [candidate("one", "A synthetic lanternword illuminates the courtyard.")])
        argv = [part for name, path in paths.items() for part in ("--" + name, str(path))]
        argv += ["--scratch", str(self.root / "scratch"), "--out", str(self.root / "out")]
        guard = SimpleNamespace(evidence={"engine": "synthetic-only"}, close=lambda: None)
        runtime = {"segmentation": diagnostic.prepare(["en"]), "japaneseBoundary": guard.evidence}
        scope = {"decks": {}, "inventory": {}, "pairs": [("en", "es")],
                 "languages": ["en"], "pairRows": {"en-es": 1}}
        original_stage = diagnostic.stage_scoped

        def fixture_stage(*args, **kwargs):
            stats = original_stage(*args, **kwargs)
            # Exercise the genuine scoped databases, but reach the analysis
            # barrier without pretending a one-row fixture is the saved pool.
            return {**stats, "inputCandidates": 11099490, "retainedCandidateRows": 33515}

        def duplicate_analysis(db, collection, lang, *_args, **_kwargs):
            choice = [61, "lanternword", selection.target_id("en", "lanternword"), "beginner"]
            for (key,) in db.execute("SELECT analysis_key FROM analysis_context"):
                db.execute("INSERT INTO context_analysis VALUES(?,?,?,?,?)",
                           (collection, lang, key, 8, json.dumps([choice, choice])))
            db.commit()
            return {}

        with patch.object(diagnostic, "scope_from_report", return_value=scope), \
                patch.object(diagnostic.admitted, "validate_pool", return_value=(pool_report, identity)), \
                patch.object(diagnostic, "verify_runtime", return_value=(runtime, guard)), \
                patch.object(diagnostic, "stage_scoped", side_effect=fixture_stage), \
                patch.object(selection, "build_context_analysis", side_effect=duplicate_analysis), \
                patch.object(selection, "pair_evidence") as pair_evidence:
            with self.assertRaisesRegex(diagnostic.DiagnosticError, "duplicate-target-choice-in-context-analysis"):
                diagnostic.run(diagnostic.parser().parse_args(argv))
        pair_evidence.assert_not_called()
        self.assertFalse((self.root / "out/SUMMARY.json").exists())

if __name__ == "__main__":
    unittest.main()
