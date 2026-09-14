#!/usr/bin/env python3
"""Deterministic checks for the Catalogue v2 storage experiment."""
from __future__ import annotations

import gzip
import json
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent


def write_gzip_jsonl(path: Path, rows: list[dict]) -> None:
    raw = b"".join(
        (json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        for row in rows
    )
    path.write_bytes(gzip.compress(raw, compresslevel=9, mtime=0))


def context(translation: str, meaning_id: str, context_id: str = "tatoeba:1") -> dict:
    return {
        "context": "We care about this work.",
        "translation": translation,
        "targetStart": 3,
        "targetEnd": 7,
        "freqRank": 900,
        "tokens": [
            {"surface": "We", "lemma": "we", "pos": "FUNC", "isContent": False},
            {"surface": "care", "lemma": "care", "pos": "WORD", "isContent": True},
        ],
        "contextId": context_id,
        "meaningId": meaning_id,
        "sourceFamily": "tatoeba",
    }


def row(deck_id: str, position: int, translation: str, meaning_id: str, with_alt: bool = False) -> dict:
    primary = context(translation, meaning_id)
    result = {
        "id": f"{deck_id}-{position:05d}",
        "text": "care",
        "targetId": "t2:en:fixture-care",
        **primary,
    }
    if with_alt:
        result["contexts"] = [
            context("Nos importa el resultado.", "tatoeba:202", "tatoeba:2")
            | {"context": "They care about the result.", "targetStart": 5, "targetEnd": 9}
        ]
    return result


def check_workflow_is_bounded() -> None:
    root = HERE.parent.parent
    workflow = (root / ".github/workflows/catalogue-v2-storage-experiment.yml").read_text(encoding="utf-8")
    for required in (
        "gh run download",
        "tools/catalog/storage_experiment.py",
        "actions: read",
        "catalogue-v2-storage-experiment",
    ):
        if required not in workflow:
            raise AssertionError(f"storage workflow missing bounded experiment contract: {required}")
    for forbidden in (
        "build_catalogue_v2.py",
        "ingest_sources.py",
        "publish.py",
        "contents: write",
    ):
        if forbidden in workflow:
            raise AssertionError(f"storage workflow must not rebuild/publish: {forbidden}")


def main() -> int:
    check_workflow_is_bounded()
    with tempfile.TemporaryDirectory(prefix="ikna-storage-experiment-") as td:
        root = Path(td)
        catalogue = root / "catalogue"
        catalogue.mkdir()

        specs = [
            ("en-es-everyday-beginner", "en", "es", "beginner", [row("en-es-everyday-beginner", 1, "Nos importa este trabajo.", "tatoeba:101", True)]),
            ("en-es-everyday-middle", "en", "es", "middle", [row("en-es-everyday-middle", 1, "Nos importa este trabajo.", "tatoeba:101")]),
            ("en-fr-everyday-beginner", "en", "fr", "beginner", [row("en-fr-everyday-beginner", 1, "Ce travail nous tient a coeur.", "tatoeba:301")]),
        ]
        decks = []
        raw_total = 0
        for deck_id, lang, meaning_lang, level, rows in specs:
            path = catalogue / f"{deck_id}.jsonl.gz"
            write_gzip_jsonl(path, rows)
            raw = b"".join(
                (json.dumps(item, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
                for item in rows
            )
            raw_total += len(raw)
            decks.append(
                {
                    "id": deck_id,
                    "title": deck_id,
                    "lang": lang,
                    "meaningLang": meaning_lang,
                    "chunkCount": len(rows),
                    "file": path.name,
                    "sizeBytes": path.stat().st_size,
                    "uncompressedSizeBytes": len(raw),
                    "subject": "",
                    "level": level,
                    "licence": "fixture",
                    "attribution": "fixture",
                    "sources": ["fixture"],
                    "phonetics": False,
                    "version": 2,
                    "collection": "everyday",
                    "sourceFamily": "tatoeba",
                }
            )

        index = {"version": 2, "catalogueVersion": 2, "builtAt": "fixture", "decks": decks, "pairs": []}
        (catalogue / "index.json").write_text(json.dumps(index), encoding="utf-8")
        (catalogue / "BUILD.json").write_text(
            json.dumps({"output": {"targetDeckMemberships": 3, "contexts": 4}}), encoding="utf-8"
        )

        report = root / "report.md"
        data = root / "report.json"
        work = root / "work"
        subprocess.run(
            [
                sys.executable,
                str(HERE / "storage_experiment.py"),
                "--dir", str(catalogue),
                "--work", str(work),
                "--report", str(report),
                "--json", str(data),
                "--modes", "pair,language",
                "--keep-work",
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        result = json.loads(data.read_text(encoding="utf-8"))
        assert result["baseline"]["deckCount"] == 3
        assert result["baseline"]["rawBytes"] == raw_total
        modes = {item["mode"]: item for item in result["modes"]}
        assert set(modes) == {"pair", "language"}
        assert modes["pair"]["memberships"] == 3
        assert modes["pair"]["contexts"] == 4
        assert modes["pair"]["losslessRows"] == 3
        assert modes["pair"]["groupCount"] == 2
        assert modes["pair"]["uniqueContextPayloads"] == 3
        assert modes["language"]["groupCount"] == 1
        assert modes["language"]["uniqueContextPayloads"] == 2
        assert modes["language"]["uniqueContextPayloads"] < modes["pair"]["uniqueContextPayloads"]
        assert (work / "pair/en-es/contexts.jsonl.gz").is_file()
        assert (work / "language/en/meanings.jsonl.gz").is_file()
        text = report.read_text(encoding="utf-8")
        assert "Lossless normalized layouts" in text
        assert "Decision:" in text

    print("Catalogue v2 storage experiment: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
