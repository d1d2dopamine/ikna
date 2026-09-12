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


def main() -> int:
    rows = [
        # Repeated exact target "sat" in distinct source contexts proves that the
        # v2 builder no longer enforces one card per written target.
        candidate("The cat sat beside the warm window.", "El gato se sentó junto a la ventana cálida.", "tatoeba:1", "tatoeba:101"),
        candidate("A small dog sat beside the open gate.", "Un perro pequeño se sentó junto a la puerta abierta.", "tatoeba:2", "tatoeba:102"),
        candidate("My friend sat beside the old fountain.", "Mi amigo se sentó junto a la fuente vieja.", "tatoeba:3", "tatoeba:103"),
        candidate("Their teacher sat beside the quiet child.", "Su profesor se sentó junto al niño tranquilo.", "tatoeba:4", "tatoeba:104"),
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

        all_cards = []
        for deck in index["decks"]:
            assert deck["file"] == deck["id"] + ".jsonl"
            assert ("-" + deck["collection"] + "-") in deck["id"]
            lines = [json.loads(line) for line in (out / deck["file"]).read_text(encoding="utf-8").splitlines() if line]
            assert len(lines) == deck["chunkCount"]
            for card in lines:
                assert card["targetId"] == target_id("en", card["text"])
                assert card["contextId"].startswith(card["sourceFamily"] + ":")
                assert utf16_slice(card["context"], card["targetStart"], card["targetEnd"]) == card["text"]
            all_cards.extend(lines)

        # No exact target may exceed the configured context cap inside this pair.
        by_target = {}
        for card in all_cards:
            by_target.setdefault((card["sourceFamily"], card["targetId"]), set()).add(card["contextId"])
        assert all(len(contexts) <= 3 for contexts in by_target.values())

        build = json.loads((out / "BUILD.json").read_text(encoding="utf-8"))
        assert build["output"]["cards"] == len(all_cards)
        assert build["output"]["uniqueSourceContexts"] > 0
        assert build["limits"]["maxContextsPerTarget"] == 3
        assert (out / "BUILD.md").exists()

    print("Catalogue v2 builder contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
