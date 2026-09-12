#!/usr/bin/env python3
"""Deterministic end-to-end checks for the Catalogue v2 builder."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from catalogue_v2 import target_id
from build_catalogue_v2 import fit_deck_to_byte_cap
from ingest.model import Candidate, Origin, write_jsonl
from segmentation import utf16_slice


def candidate(context: str, meaning: str, context_ref: str, meaning_ref: str, collection="everyday") -> Candidate:
    family = "tatoeba" if collection == "everyday" else "wikimatrix"
    version = "fixture-1" if family == "tatoeba" else "v1"
    return Candidate(
        collection=collection,
        lang="en",
        meaning_lang="es",
        context=context,
        meaning=meaning,
        origins=[Origin(family, version, context_ref, meaning_ref, alignment_score=1.2 if family == "wikimatrix" else None)],
    )


def check_deck_byte_cap_keeps_targets() -> None:
    # A rich deck may exceed the static 24 MiB production ceiling only because
    # of optional contexts.  The fitter must remove alternatives, never primary
    # contexts or learning targets.  Use a tiny cap here so the regression test
    # stays fast.
    targets = []
    for i in range(6):
        targets.append(
            {
                "targetId": f"t2:en:{i}",
                "text": f"target-{i}",
                "context": f"Primary context {i}.",
                "translation": "meaning",
                "targetStart": 0,
                "targetEnd": 6,
                "freqRank": i + 1,
                "tokens": [],
                "contextId": f"tatoeba:{i}",
                "meaningId": f"tatoeba:{100+i}",
                "sourceFamily": "tatoeba",
                "contexts": [
                    {
                        "context": "A" * 900,
                        "translation": "B" * 500,
                        "targetStart": 0,
                        "targetEnd": 1,
                        "freqRank": i + 1,
                        "tokens": [],
                        "contextId": f"tatoeba:{i}:alt:{j}",
                        "meaningId": f"tatoeba:{100+i}:alt:{j}",
                        "sourceFamily": "tatoeba",
                    }
                    for j in range(2)
                ],
            }
        )
    before_targets = [target["targetId"] for target in targets]
    before_contexts = sum(1 + len(target.get("contexts", [])) for target in targets)
    size, removed = fit_deck_to_byte_cap("fixture-deck", targets, max_bytes=7_000)
    assert size <= 7_000
    assert removed > 0
    assert [target["targetId"] for target in targets] == before_targets
    assert all(target.get("context") for target in targets)
    assert sum(1 + len(target.get("contexts", [])) for target in targets) == before_contexts - removed


def main() -> int:
    check_deck_byte_cap_keeps_targets()
    rows = [
        # Repeated exact target "zebra" in distinct source contexts proves that the
        # v2 builder no longer enforces one card per written target.
        candidate("We often walk near the zebra.", "A menudo caminamos cerca de la cebra.", "tatoeba:1", "tatoeba:101"),
        candidate("We often walk near the zebra!", "A menudo caminamos cerca de la cebra!", "tatoeba:2", "tatoeba:102"),
        candidate("We often walk near the zebra?", "A menudo caminamos cerca de la cebra?", "tatoeba:3", "tatoeba:103"),
        candidate("We often walk near the zebra...", "A menudo caminamos cerca de la cebra...", "tatoeba:4", "tatoeba:104"),
        candidate("We study the zebra.", "Estudiamos la cebra.", "wikimatrix:v1:en-es:0:a", "wikimatrix:v1:en-es:0:b", "knowledge"),
        candidate("Water freezes at zero degrees Celsius.", "El agua se congela a cero grados Celsius.", "wikimatrix:v1:en-es:1:a", "wikimatrix:v1:en-es:1:b", "knowledge"),
        candidate("The city lies beside a wide river.", "La ciudad se encuentra junto a un río ancho.", "wikimatrix:v1:en-es:2:a", "wikimatrix:v1:en-es:2:b", "knowledge"),
    ]
    with tempfile.TemporaryDirectory(prefix="ikna-v2-builder-") as td:
        root = Path(td)
        candidates = root / "candidates.jsonl.gz"
        write_jsonl(str(candidates), rows)
        out = root / "out"
        subprocess.run(
            [
                sys.executable,
                str(HERE / "build_catalogue_v2.py"),
                "--candidates", str(candidates),
                "--out", str(out),
                "--learn", "en",
                "--meanings", "es",
                "--min-deck", "1",
                "--max-deck", "20",
                "--function-top", "0",
                "--contexts-per-target", "3",
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        index = json.loads((out / "index.json").read_text(encoding="utf-8"))
        assert index["version"] == 2 and index["catalogueVersion"] == 2
        assert {row["collection"] for row in index["collectionPairs"]} == {"everyday", "knowledge"}
        assert {row["id"] for row in index["sourceFamilies"]} == {"tatoeba", "wikimatrix"}
        assert len(index["decks"]) >= 2

        all_targets = []
        all_contexts = []
        target_collections = {}
        for deck in index["decks"]:
            assert deck["file"] == deck["id"] + ".jsonl"
            assert ("-" + deck["collection"] + "-") in deck["id"]
            lines = [json.loads(line) for line in (out / deck["file"]).read_text(encoding="utf-8").splitlines() if line]
            assert len(lines) == deck["chunkCount"]
            for card in lines:
                assert card["targetId"] == target_id("en", card["text"])
                assert card["contextId"].startswith(card["sourceFamily"] + ":")
                assert utf16_slice(card["context"], card["targetStart"], card["targetEnd"]) == card["text"]
                contexts = [card] + list(card.get("contexts") or [])
                assert len(contexts) <= 3
                seen = set()
                for context in contexts:
                    assert context["contextId"] not in seen
                    seen.add(context["contextId"])
                    selected = utf16_slice(context["context"], context["targetStart"], context["targetEnd"])
                    assert target_id("en", selected) == card["targetId"]
                    all_contexts.append((card["targetId"], context["contextId"]))
            all_targets.extend(lines)
            for card in lines:
                target_collections.setdefault(card["targetId"], set()).add(deck["collection"])

        # targetId is catalogue-global: the same written target keeps the same
        # identity when it appears in another collection/deck.
        zebra_id = target_id("en", "zebra")
        assert target_collections.get(zebra_id) == {"everyday", "knowledge"}

        # Repeated exact targets are represented by one scheduling row with
        # additional natural contexts, never by duplicate rows in one deck.
        for deck in index["decks"]:
            lines = [json.loads(line) for line in (out / deck["file"]).read_text(encoding="utf-8").splitlines() if line]
            ids = [card["targetId"] for card in lines]
            assert len(ids) == len(set(ids))
        assert any(card.get("contexts") for card in all_targets)

        build = json.loads((out / "BUILD.json").read_text(encoding="utf-8"))
        assert build["output"]["targetDeckMemberships"] == len(all_targets)
        assert build["output"]["contexts"] == len(all_contexts)
        assert build["output"]["uniqueSourceContexts"] > 0
        assert build["limits"]["maxContextsPerTarget"] == 3
        assert (out / "BUILD.md").exists()

    print("Catalogue v2 builder contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
