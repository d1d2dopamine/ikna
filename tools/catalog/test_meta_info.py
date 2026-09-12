#!/usr/bin/env python3
"""Small deterministic checks for tools/catalog/meta_info.py."""

import json
import tempfile
import unittest
from pathlib import Path

import meta_info


def card(card_id, text, context, source, lemma=None):
    start = context.lower().index(text.lower())
    prefix = context[:start]
    surface = context[start : start + len(text)]
    # The fixture is ASCII, so character and UTF-16-unit offsets are identical.
    return {
        "id": card_id,
        "text": surface,
        "context": context,
        "translation": "translation\n— Tatoeba #" + str(source),
        "targetStart": start,
        "targetEnd": start + len(surface),
        "freqRank": 100,
        "tokens": [
            {
                "surface": surface,
                "lemma": lemma if lemma is not None else surface.lower(),
                "pos": "WORD",
                "isContent": True,
            }
        ],
    }


def write_deck(root, name, records):
    path = root / name
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


class MetaInfoTests(unittest.TestCase):
    def test_same_source_in_two_meaning_decks_is_not_a_new_context(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_deck(root, "en-ru-beginner.jsonl", [card("a", "care", "I care about this.", 1)])
            write_deck(root, "en-es-beginner.jsonl", [card("b", "care", "I care about this.", 1)])
            data, groups = meta_info.analyse(root)
            self.assertEqual(data["targets"]["uniqueExactTargets"], 1)
            self.assertEqual(data["targets"]["uniqueTargetSourcePairs"], 1)
            self.assertEqual(data["targets"]["targetsWithAtLeast2Contexts"], 0)
            self.assertEqual(groups, [])

    def test_same_exact_target_in_new_source_sentence_is_a_context_candidate(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_deck(root, "en-ru-beginner.jsonl", [card("a", "care", "I care about this.", 1)])
            write_deck(root, "en-es-beginner.jsonl", [card("b", "Care", "Care can take time.", 2)])
            data, groups = meta_info.analyse(root)
            self.assertEqual(data["targets"]["uniqueExactTargets"], 1)
            self.assertEqual(data["targets"]["targetsWithAtLeast2Contexts"], 1)
            self.assertEqual(len(groups), 1)
            self.assertEqual(len(groups[0].source_keys), 2)

    def test_real_lemma_metadata_is_measured_without_using_it_for_grouping(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_deck(root, "en-ru-beginner.jsonl", [card("a", "ran", "I ran home today.", 1, lemma="run")])
            data, _ = meta_info.analyse(root)
            self.assertEqual(data["metadata"]["tokensWithNonIdentityLemma"], 1)
            self.assertEqual(data["targets"]["uniqueExactTargets"], 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
