#!/usr/bin/env python3
"""Exact overlap-reuse regressions against a frozen independent chooser.

Run directly or with unittest discovery. No corpus/network access, optional source
folder, generated result file or production cache clearing. Real CJK ICU is
required as in the repository segmentation suite; missing ICU is not a pass.
"""
from __future__ import annotations

from copy import deepcopy
from collections import Counter
import hashlib
import itertools
import json
import os
from pathlib import Path
import random
import sqlite3
import subprocess
import sys
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
import importlib.util
import inspect
import selection_policy as candidate
import segmentation
import selection_experiment as selection
import replenishment_review

# Load a separate, exact test-only reference; never alias the production chooser.
_reference_spec = importlib.util.spec_from_file_location(
    "_selection_policy_before_overlap", HERE / "fixtures" / "selection_policy_before_overlap.py")
reference = importlib.util.module_from_spec(_reference_spec)
_reference_spec.loader.exec_module(reference)

TEST_DIAGNOSTICS = {"comparisonInvocations": 0, "randomSeed": 320012, "callReductionCases": []}


def context(ref, text, families=("tatoeba",), score=None, tokens=10, **extra):
    return {"candidateId": ref, "contextId": ref, "context": text,
            "meaningId": "meaning:" + str(ref), "meaning": "Fixture only.",
            "origins": [{"sourceFamily": f, "sourceVersion": "fixture-only",
                         "contextRef": ref, "meaningRef": "meaning:" + str(ref)} for f in families],
            "sourceFamilies": list(families), "alignmentScore": score, "tokenCount": tokens, **extra}


class DifferentialTests(unittest.TestCase):
    def test_frozen_reference_provenance_and_independence(self):
        source = inspect.getsource(reference.choose_contexts)
        self.assertEqual(hashlib.sha256(source.encode()).hexdigest(), reference.FROZEN_CHOOSER_SHA256)
        self.assertEqual(reference.SOURCE_POLICY_SHA256,
                         "6100810b5bd7eca23b063f90dd0c1e81cb3a7f55ba3aeab76f02be84720fb3ae")
        self.assertIsNot(reference.choose_contexts, candidate.choose_contexts)
        self.assertIs(reference.choose_contexts.__globals__, reference.__dict__)
        self.assertIsNot(reference.choose_contexts.__code__, candidate.choose_contexts.__code__)
        self.assertIs(reference.context_overlap, candidate.context_overlap)
        self.assertIs(reference.near_duplicate, candidate.near_duplicate)
        self.assertIs(reference.context_quality_key, candidate.context_quality_key)

    def test_differential_assertions_detect_identity_order_and_counter_mutants(self):
        rows = [context("a", "Alpha beta words."), context("b", "Gamma delta text."),
                context("c", "Epsilon zeta sentence.")]
        chooser = candidate.choose_contexts
        for mutation in ("identity", "order", "counter"):
            def mutant(*args, **kwargs):
                selected, rejected = chooser(*args, **kwargs)
                if mutation == "identity":
                    return [dict(c) for c in selected], rejected
                if mutation == "order":
                    return list(reversed(selected)), rejected
                return selected, rejected + 1
            with self.subTest(mutation=mutation), patch.object(candidate, "choose_contexts", mutant):
                with self.assertRaises(AssertionError):
                    self.compare(rows, 3, 1.1)

    def compare(self, rows, limit=12, threshold=.85, diverse=True, lang="en", trace=False):
        """Compare every returned field, reference identity/order, and input state."""
        before = deepcopy(rows)
        identities = list(map(id, rows))
        outcomes, calls, duplicates, caches = [], [], [], []
        for module in (reference, candidate):
            cache_before = candidate._written_counts.cache_info()._asdict()
            overlap_calls, duplicate_calls = [], []
            overlap, duplicate = candidate.context_overlap, candidate.near_duplicate
            def counted_overlap(a, b, l):
                overlap_calls.append((a, b, l))
                return overlap(a, b, l)
            def counted_duplicate(a, b, t):
                duplicate_calls.append((a, b, t))
                return duplicate(a, b, t)
            with patch.object(module, "context_overlap", counted_overlap), \
                    patch.object(module, "near_duplicate", counted_duplicate):
                try:
                    chosen, rejected = module.choose_contexts(iter(rows), limit, threshold, diverse=diverse, lang=lang)
                    outcome = ("ok", chosen, rejected, list(map(id, chosen)))
                except Exception as exc:
                    outcome = ("error", type(exc).__name__, str(exc))
            outcomes.append(outcome)
            calls.append(overlap_calls)
            duplicates.append(duplicate_calls)
            cache_after = candidate._written_counts.cache_info()._asdict()
            caches.append({"hitsDelta": cache_after["hits"] - cache_before["hits"],
                           "missesDelta": cache_after["misses"] - cache_before["misses"],
                           "endSize": cache_after["currsize"], "maxsize": cache_after["maxsize"]})
            self.assertEqual(rows, before)
            self.assertEqual(list(map(id, rows)), identities)
        self.assertEqual(outcomes[0], outcomes[1])
        self.assertEqual(duplicates[0], duplicates[1], "Near-duplicate call order/threshold must stay exact")
        self.assertLessEqual(len(calls[1]), len(calls[0]))
        TEST_DIAGNOSTICS["comparisonInvocations"] += 1
        if trace:
            return outcomes[0], calls, caches
        return outcomes[0]

    def test_empty_single_limits_and_missing_language(self):
        for rows in ([], [context("a", "One complete sentence.")],
                     [context("a", "One complete sentence."), context("b", "Several other words.")]):
            for limit, diverse, lang in itertools.product((-3, 0, 1, 2, 12, 100), (False, True), (None, "", "en")):
                with self.subTest(n=len(rows), limit=limit, diverse=diverse, lang=lang):
                    self.compare(rows, limit, diverse=diverse, lang=lang)
        # Preserve surprising original placement: a ranking can require lang
        # even after limit=1 has been reached, if another source is available.
        rows = [context("a", "First sentence.", ("x", "y")),
                context("b", "Other sentence.", ("z",))]
        self.assertEqual(self.compare(rows, 1, lang=None)[0], "error")

    def test_known_diversity_and_near_duplicate_rejection(self):
        rows = [context("a", "Если бы не вы, меня бы сегодня здесь не было.", tokens=10),
                context("b", "Если бы не ты, меня бы сегодня здесь не было.", tokens=10),
                context("c", "Я сегодня купил новую книгу в магазине.", tokens=7)]
        result = self.compare(rows, 2, lang="ru")
        self.assertEqual(result[1][1]["contextId"], "c")
        rows = [context("a", "If I were a bird, I could fly to you.", tokens=11),
                context("b", "If I were a bird, I would fly to you.", tokens=11)]
        self.assertEqual(len(self.compare(rows, 3)[1]), 2)
        rejected = [context(str(i), "Identical sentence for every record.") for i in range(8)]
        self.assertEqual(self.compare(rejected, 12)[2], 7)

    def test_repeated_references_equal_copies_duplicate_ids_and_text(self):
        first = context("same", "Alpha beta gamma delta.")
        rows = [first, first, deepcopy(first),
                context("same", "Different natural words here."),
                context("distinct", first["context"]),
                context(None, first["context"]),
                context("", first["context"]),
                context("z", "Few other fresh words.")]
        for limit, threshold, diverse in itertools.product((1, 2, 3, 12), (0, .5, .85, 1, 1.1), (False, True)):
            self.compare(rows, limit, threshold, diverse)
        # Equal copies remain equal under policy; returned original object stays
        # the first equal entry in stable traversal, never a reconstructed dict.
        result = self.compare([first, deepcopy(first), first], 12)
        self.assertIs(result[1][0], first)
        self.assertEqual(result[2], 0)

    def test_rejection_counter_deduplicates_keys_not_occurrences(self):
        rows = [context("best", "Exact same context sentence.", ("x", "y", "z")),
                context("rejected", "Exact same context sentence.", ("x",)),
                context("rejected", "Exact same context sentence.", ("y",)),
                context("other", "Exact same context sentence.", ("z",))]
        result = self.compare(rows, 12)
        self.assertEqual(result[2], 2)

    def test_stable_ties_permutations_and_source_priority(self):
        # Equal keys and zero-overlap ties; marker is outside the rank key.
        rows = [context("tie", text, marker=i) for i, text in enumerate(("aaaa bbbb.", "cccc dddd.", "eeee ffff."))]
        self.assertEqual(len({candidate.context_quality_key(c) for c in rows}), 1)
        for order in itertools.permutations(rows):
            result = self.compare(list(order), 3)
            self.assertEqual(result[3], list(map(id, order)))
        rows += [context("priority", "aaaa cccc.", ("one", "two"), -10),
                 context("alignment", "aaaa eeee.", ("one", "two"), 5)]
        self.assertEqual(self.compare(rows, 3)[1][0]["candidateId"], "alignment")

    def test_first_pass_filters_and_second_pass_restores_candidates(self):
        rows = [context("best", "These words carry strongest evidence.", ("a", "b"), 10),
                context("alternate", "These words carry different evidence.", ("a",), 3),
                context("other", "Uniquely varied unrelated lexical inventory.", ("c",), -20),
                context("none", "New sentence without source families.", (), 100)]
        result = self.compare(rows, 4)
        self.assertEqual([c["candidateId"] for c in result[1]][:2], ["best", "other"])
        self.assertEqual(len(result[1]), 4)
        for row in rows:
            row["sourceFamilies"] = []
        self.compare(rows, 3)

    def test_unicode_all_supported_languages_empty_tokens_and_normalization(self):
        texts = {
            "en": ["I'm learning co-operating words.", "I’m learning co-operating words.", "😀 café A\u030a Straße Ａ ｶ 𠀀"],
            "de": ["Heute lese ich Straße auf dem Schild.", "Heute lese ich STRASSE auf dem Schild.", "Übermorgen wäre ein schöner Tag."],
            "ru": ["Я читаю новую книгу сегодня.", "Мы читаем старую книгу завтра.", "Съешь ещё этих мягких французских булок."],
            "pl": ["Zażółć gęślą jaźń przy oknie.", "Czytam książkę każdego wieczoru.", "Żółw patrzy na wielką rzekę."],
            "es": ["El niño está leyendo un libro.", "Mañana veremos una película.", "¿Dónde está la estación?"],
            "fr": ["L’élève apprend une nouvelle langue.", "Je voudrais écrire à ma sœur.", "Demain nous irons au marché."],
            "it": ["Leggo un libro nella città.", "Domani visiteremo il museo.", "L’acqua è già nella bottiglia."],
            "pt": ["A criança está lendo um livro.", "Amanhã iremos à estação.", "Esta ação terá uma consequência."],
            "zh": ["我喜欢学习中文。", "今天我们一起读书。", "𠀀😀我喜欢看新电影。"],
            "ja": ["私は日本語を学びます。", "今日は新しい本を読みます。", "今日はＡとAとカとｶを見た。"],
            "ko": ["저는 한국어를 배웁니다.", "오늘 우리는 새로운 책을 읽습니다.", "내일 친구와 영화를 봅니다."],
        }
        info = segmentation.prepare(texts)
        self.assertEqual(info["engine"], "ICU word break")
        self.assertTrue(info["icuVersion"])
        TEST_DIAGNOSTICS["segmentation"] = info
        for lang, values in texts.items():
            rows = [context(str(i), text) for i, text in enumerate(values + ["", "!!! 😀", "1234", values[0]])]
            for limit, threshold in itertools.product((1, 2, 3, 12), (.0, .85, 1.1)):
                self.compare(rows, limit, threshold, lang=lang)

    def test_exact_threshold_boundaries(self):
        rows = [context("a", "one two three four five"), context("b", "one two three four six"),
                context("c", "six seven eight nine ten")]
        boundary = 4 / 6
        for threshold in (-1, 0, boundary - 1e-15, boundary, boundary + 1e-15, .85, 1, 1.1, float("inf"), float("nan")):
            self.compare(rows, 12, threshold)

    def test_exhaustive_tiny_lists(self):
        base = [context("a", "one two three", ("a",)),
                context("b", "one two four", ("b",)),
                context("c", "five six seven", ()),
                context("c", "one two three", ("a", "b"))]
        for length in range(5):
            for indices in itertools.product(range(4), repeat=length):
                rows = [base[i] for i in indices]
                for limit in (0, 1, 2, 4):
                    self.compare(rows, limit, .5)

    def test_deterministic_random_fixtures(self):
        rng = random.Random(TEST_DIAGNOSTICS["randomSeed"])
        words = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron".split()
        for case in range(1500):
            rows = []
            for i in range(rng.randrange(0, 45)):
                if rows and rng.random() < .20:
                    row = rng.choice(rows)
                    rows.append(row if rng.random() < .5 else deepcopy(row))
                    continue
                text = " ".join(rng.choices(words, k=rng.randrange(0, 17))) + rng.choice((".", "!", "", " 😀"))
                rows.append(context(str(rng.randrange(20)), text,
                                    rng.choice(((), ("a",), ("b",), ("a", "b"), ("a", "a"))),
                                    rng.choice((None, -1., 0., .1, .5, 1.)),
                                    rng.choice((None, 0, 1, 5, 10, 15, "10")), marker=i))
            rng.shuffle(rows)
            with self.subTest(case=case):
                self.compare(rows, rng.choice((-1, 0, 1, 2, 3, 5, 12, 50)),
                             rng.choice((0., .25, .5, .85, 1., 1.1)),
                             rng.choice((False, True)), rng.choice(("en", "de", "ru")))

    def test_overlap_call_reduction_and_original_duplicate_trace(self):
        rows = [context(str(i), f"Target observation {chr(0x410+i)} {chr(0x450+i)} fresh material.") for i in range(37)]
        for threshold in (.85, 1.1):
            result, calls, caches = self.compare(rows, 12, threshold, trace=True)
            self.assertEqual(result[0], "ok")
            self.assertLess(len(calls[1]), len(calls[0]) // 3)
            self.assertLessEqual(len(calls[1]), len({id(c) for c in rows}) * len(result[1]))
            self.assertNotEqual(caches[0]["hitsDelta"], caches[1]["hitsDelta"])
            TEST_DIAGNOSTICS["callReductionCases"].append({"candidateObjects": len(rows), "limit": 12,
                "threshold": threshold, "selected": len(result[1]), "rejected": result[2],
                "originalOverlapCalls": len(calls[0]), "candidateOverlapCalls": len(calls[1]),
                "writtenCountCache": {"original": caches[0], "candidate": caches[1]}})

    def test_natural_lru_eviction_and_fresh_reused_process_outputs(self):
        outputs = []
        for mode in ("original", "candidate"):
            completed = subprocess.run([sys.executable, "-B", str(HERE / "fixtures" / "selection_overlap_cache_probe.py"), mode],
                                       text=True, capture_output=True, check=True, env=os.environ.copy())
            outputs.append(json.loads(completed.stdout))
        self.assertEqual(outputs[0]["outputs"], outputs[1]["outputs"])
        self.assertEqual(outputs[0]["initialCache"], outputs[1]["initialCache"])
        self.assertEqual(outputs[0]["initialCache"]["currsize"], 0)
        self.assertTrue(all(p["cache"]["currsize"] <= 8192 for o in outputs for p in o["phases"]))
        self.assertNotEqual(outputs[0]["phases"], outputs[1]["phases"])
        TEST_DIAGNOSTICS["naturalCachePopulation"] = {o["mode"]: {k: v for k, v in o.items() if k not in ("mode", "outputs")}
                                              for o in outputs}

    def test_call_local_state_bounds_and_no_cross_call_reuse(self):
        rows = [context(str(i), f"Unique {chr(0x410+i)} material here.") for i in range(30)]
        captures = []
        def trace(frame, event, arg):
            if frame.f_code is candidate.choose_contexts.__code__ and event == "return":
                captures.append((dict(frame.f_locals["maxima"]), list(frame.f_locals["selected"])))
            return trace
        previous = sys.gettrace()
        try:
            sys.settrace(trace)
            candidate.choose_contexts(rows, 12, 1.1, diverse=True, lang="en")
        finally:
            sys.settrace(previous)
        states, selected = captures[0]
        self.assertLessEqual(len(states), len({id(c) for c in rows}))
        self.assertTrue(all(isinstance(k, int) and len(v) == 2 and 0 < v[0] <= len(selected) for k, v in states.items()))
        # Changing the same object between calls is valid and must be observed.
        self.compare(rows, 12, 1.1)
        rows[0]["context"] = rows[1]["context"]
        self.compare(rows, 12, .85)

    def test_failures_preserve_original_exception(self):
        for broken in ({"context": "text", "tokenCount": "not-an-int"},
                       {"context": "text", "alignmentScore": "not-a-number"},
                       {"context": None}, {"candidateId": "missing-text"}):
            self.compare([context("good", "Valid text words."), broken], 12)

    def test_pair_pruning_counters_quarantine_and_target_allocation(self):
        db = sqlite3.connect(":memory:")
        self.addCleanup(db.close)
        db.executescript(selection.SCHEMA)
        quarantine_key, quarantine = next(iter(candidate.quarantine_rules().items()))
        for i in range(97):
            text = f"We study target {chr(0x410+i)} {chr(0x490+i)} using unique example words."
            meaning = f"Synthetische Übersetzung für Prüfung {i}."
            families = ["tatoeba"]
            origins = [{"sourceFamily": "tatoeba", "sourceVersion": "fixture-only",
                        "contextRef": "fixture:" + str(i), "meaningRef": "meaning:" + str(i)}]
            choices = [[100, "target", "target-a", "beginner"], [200, "words", "target-b", "middle"]]
            if i == 93:
                text = "x"
            elif i == 94:
                meaning = ""
            elif i == 95:
                choices = []
            elif i == 96:
                text = quarantine["text"]
                origins[0].update(sourceFamily=quarantine_key[0], sourceVersion=quarantine_key[1], contextRef=quarantine_key[2])
            db.execute("INSERT INTO candidate VALUES(?,?,?,?,?,?,?,?,?,?)",
                       (f"candidate:{i:03}", "everyday", "en", "de", f"key:{i}", text, meaning, json.dumps(origins), None, f"key:{i}"))
            db.execute("INSERT INTO context_analysis VALUES(?,?,?,?,?)",
                       ("everyday", "en", f"key:{i}", 10, json.dumps(choices)))
        db.commit()
        with patch.object(selection, "choose_contexts", reference.choose_contexts):
            expected = selection.pair_evidence(db, "everyday", "en", "de", None, 12, diverse=True)
        with patch.object(selection, "choose_contexts", candidate.choose_contexts):
            actual = selection.pair_evidence(db, "everyday", "en", "de", None, 12, diverse=True)
        self.assertEqual(expected, actual)
        for field in ("sentenceLengthRejected", "translationLengthRejected", "noUsableTarget", "sourceQuarantinedCandidateRows"):
            self.assertEqual(actual[1][field], 1)
        self.assertEqual(actual[1]["eligibleTargetOccurrences"], 186)
        self.assertTrue(all(len(t["contexts"]) <= 36 for t in actual[0]))
        with patch.object(replenishment_review, "choose_contexts", reference.choose_contexts):
            allocated = replenishment_review.allocate(expected[0], "en")
        with patch.object(replenishment_review, "choose_contexts", candidate.choose_contexts):
            self.assertEqual(replenishment_review.allocate(actual[0], "en"), allocated)
        payload = json.dumps({"targets": actual[0], "stats": actual[1]}, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()
        TEST_DIAGNOSTICS["syntheticPair"] = {"rows": 97, "stats": dict(actual[1]), "returnedEvidenceSha256": hashlib.sha256(payload).hexdigest(),
                                    "allocationExact": True, "note": "Generated fixture, not catalogue material or real-prefix evidence."}


if __name__ == "__main__":
    unittest.main(verbosity=2)
