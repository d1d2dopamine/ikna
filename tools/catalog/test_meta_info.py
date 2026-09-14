#!/usr/bin/env python3
"""Small deterministic checks for tools/catalog/meta_info.py."""

import json
import gzip
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
    opener = gzip.open if name.endswith(".gz") else open
    with opener(path, "wt", encoding="utf-8") as handle:
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


    def test_v2_context_id_and_collection_are_used_directly(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first = card("a", "care", "I care about this.", 1)
            first.pop("translation")
            first["translation"] = "translation without legacy suffix"
            first.update({"contextId":"wikimatrix:v1:en-es:1:a","meaningId":"wikimatrix:v1:en-es:1:b","sourceFamily":"wikimatrix"})
            second = card("b", "care", "They care about it.", 2)
            second.pop("translation")
            second["translation"] = "another translation"
            second.update({"contextId":"wikimatrix:v1:en-es:2:a","meaningId":"wikimatrix:v1:en-es:2:b","sourceFamily":"wikimatrix"})
            write_deck(root, "en-es-knowledge-beginner.jsonl", [first, second])
            data, groups = meta_info.analyse(root)
            self.assertEqual(data["metadata"]["provenanceMissing"], 0)
            self.assertEqual(data["breakdown"]["cardsByCollection"]["knowledge"], 2)
            self.assertEqual(data["targets"]["targetsWithAtLeast2Contexts"], 1)
            self.assertEqual(len(groups), 1)


    def test_v2_grouped_contexts_count_as_one_target_membership(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first = card("a", "care", "I care about this.", 1)
            first.update({"targetId":"t2:en:0000000000000000","contextId":"tatoeba:1","meaningId":"tatoeba:101","sourceFamily":"tatoeba"})
            alt = card("ignored", "care", "They care about it.", 2)
            first["contexts"] = [{
                "context": alt["context"], "translation": alt["translation"],
                "targetStart": alt["targetStart"], "targetEnd": alt["targetEnd"],
                "freqRank": alt["freqRank"], "tokens": alt["tokens"],
                "contextId": "tatoeba:2", "meaningId": "tatoeba:102", "sourceFamily": "tatoeba"
            }]
            write_deck(root, "en-ru-everyday-beginner.jsonl", [first])
            data, groups = meta_info.analyse(root)
            self.assertEqual(data["catalogue"]["targetDeckMemberships"], 1)
            self.assertEqual(data["catalogue"]["contexts"], 2)
            self.assertEqual(data["targets"]["targetsWithAtLeast2Contexts"], 1)
            self.assertEqual(len(groups), 1)

    def test_real_lemma_metadata_is_measured_without_using_it_for_grouping(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_deck(root, "en-ru-beginner.jsonl", [card("a", "ran", "I ran home today.", 1, lemma="run")])
            data, _ = meta_info.analyse(root)
            self.assertEqual(data["metadata"]["tokensWithNonIdentityLemma"], 1)
            self.assertEqual(data["targets"]["uniqueExactTargets"], 1)

    def test_gzip_v2_assets_are_analysed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_deck(root, "en-ru-everyday-beginner.jsonl.gz", [card("a", "care", "I care about this.", 1)])
            data, _ = meta_info.analyse(root)
            self.assertEqual(data["input"]["deckFiles"], 1)
            self.assertEqual(data["catalogue"]["targetDeckMemberships"], 1)

    def test_human_inventory_exposes_cards_last_deck_and_breakdowns(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first = card("a", "care", "I care about this.", 1, lemma="care")
            first.update({"targetId":"t2:en:0000000000000000","contextId":"tatoeba:1","meaningId":"tatoeba:101","sourceFamily":"tatoeba"})
            second = card("b", "work", "We work together.", 2, lemma="work")
            second.update({"targetId":"t2:en:1111111111111111","contextId":"tatoeba:2","meaningId":"tatoeba:102","sourceFamily":"tatoeba"})
            write_deck(root, "en-ru-everyday-beginner.jsonl", [first])
            write_deck(root, "en-es-everyday-middle.jsonl", [first | {"id":"c"}, second])
            decks = []
            for deck_id, file_name, level, meaning, contexts in (
                ("en-ru-everyday-beginner", "en-ru-everyday-beginner.jsonl", "beginner", "ru", 1),
                ("en-es-everyday-middle", "en-es-everyday-middle.jsonl", "middle", "es", 2),
            ):
                path = root / file_name
                decks.append({
                    "id": deck_id, "file": file_name, "lang": "en", "meaningLang": meaning,
                    "collection": "everyday", "level": level, "sourceFamily": "tatoeba",
                    "chunkCount": 1 if meaning == "ru" else 2, "contextCount": contexts,
                    "sizeBytes": path.stat().st_size, "uncompressedSizeBytes": path.stat().st_size,
                })
            (root / "index.json").write_text(json.dumps({
                "version": 2, "catalogueVersion": 2, "builtAt": "fixture", "decks": decks,
                "pairs": [{"lang":"en","meaningLang":"ru"},{"lang":"en","meaningLang":"es"}],
            }), encoding="utf-8")
            data, groups, samples = meta_info._analyse(root, sample_per_deck=1)
            self.assertEqual(data["catalogue"]["cardsInDecks"], 3)
            self.assertEqual(data["inventory"]["lastIndexDeck"]["id"], "en-es-everyday-middle")
            self.assertEqual(data["inventory"]["lastIndexDeck"]["cards"], 2)
            self.assertEqual(data["breakdown"]["byCollection"][0]["cards"], 3)
            self.assertEqual(data["breakdown"]["byCollection"][0]["uniqueTargets"], 2)
            self.assertEqual(len(samples), 2)
            report = meta_info.markdown_report(data, groups, 0)
            self.assertIn("cards in all decks", report)
            self.assertIn("Full deck inventory", report)
            self.assertIn("en-es-everyday-middle", report)

    def test_build_cross_check_adds_target_cap_inventory(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            row = card("a", "care", "I care about this.", 1)
            write_deck(root, "en-ru-everyday-beginner.jsonl", [row])
            path = root / "en-ru-everyday-beginner.jsonl"
            (root / "index.json").write_text(json.dumps({
                "version": 1, "builtAt": "fixture",
                "decks": [{"id":"en-ru-everyday-beginner","file":path.name,"lang":"en","meaningLang":"ru",
                           "level":"beginner","collection":"everyday","sourceFamily":"tatoeba",
                           "chunkCount":1,"contextCount":1,"sizeBytes":path.stat().st_size,
                           "uncompressedSizeBytes":path.stat().st_size}],
                "pairs": [{"lang":"en","meaningLang":"ru"}],
            }), encoding="utf-8")
            data, _ = meta_info.analyse(root)
            build = root / "BUILD.json"
            build.write_text(json.dumps({
                "output": {
                    "targetDeckMemberships":1,"contexts":1,"uniqueSourceContexts":1,"uniqueTargets":1,
                    "decks":1,"compressedDeckBytes":path.stat().st_size,"uncompressedDeckBytes":path.stat().st_size,
                },
                "limits":{"maxDeckTargets":1},
            }), encoding="utf-8")
            meta_info.verify_build(data, build)
            self.assertTrue(data["input"]["buildVerified"])
            self.assertEqual(data["inventory"]["decksAtTargetCap"], 1)

    def test_v2_target_identity_mismatch_is_audited(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            row = card("a", "care", "I care about this.", 1)
            row.update({"targetId":"t2:en:ffffffffffffffff","contextId":"tatoeba:1","meaningId":"tatoeba:101","sourceFamily":"tatoeba"})
            write_deck(root, "en-ru-everyday-beginner.jsonl", [row])
            path = root / "en-ru-everyday-beginner.jsonl"
            (root / "index.json").write_text(json.dumps({
                "version":2,"catalogueVersion":2,"targetIdentity":{"version":2,"method":"nfkc-casefold-exact"},
                "decks":[{"id":"en-ru-everyday-beginner","file":path.name,"lang":"en","meaningLang":"ru",
                          "level":"beginner","collection":"everyday","sourceFamily":"tatoeba",
                          "chunkCount":1,"contextCount":1,"sizeBytes":path.stat().st_size,
                          "uncompressedSizeBytes":path.stat().st_size}],"pairs":[]
            }), encoding="utf-8")
            data, _ = meta_info.analyse(root)
            self.assertEqual(data["metadata"]["targetIdMismatches"], 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
