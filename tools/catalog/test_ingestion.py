#!/usr/bin/env python3
"""Contract tests for Catalogue v2 source ingestion."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))

from ingest.adapters import (
    infer_wikimatrix_tsv_languages,
    iter_globalvoices,
    iter_tatoeba,
    iter_tatoeba_matrix,
    iter_wikimatrix,
    iter_wikimatrix_tsv,
    iter_wikimatrix_tsv_pair,
)
from ingest.model import Candidate, Origin, merge_candidate_files, merge_candidates, read_jsonl, write_jsonl
from ingest.registry import SourceRegistry

REGISTRY = HERE / "sources" / "catalogue-v2-sources.json"
FIXTURES = HERE / "fixtures" / "ingest"
CLI = HERE / "ingest_sources.py"


def expect_raises(fn, needle: str) -> None:
    try:
        fn()
    except Exception as exc:
        assert needle in str(exc), (needle, exc)
    else:
        raise AssertionError("expected failure containing %r" % needle)


def test_registry() -> SourceRegistry:
    registry = SourceRegistry.load(str(REGISTRY))
    assert set(registry.policies) == {"tatoeba", "wikimatrix", "globalvoices"}
    assert registry.get("tatoeba").collection == "everyday"
    assert registry.get("tatoeba").requires_explicit_source_version is True
    assert registry.get("wikimatrix").licence_id == "CC-BY-SA-4.0"
    assert registry.get("globalvoices").required_record_attribution == ("articleUrl", "contributors")

    raw = json.loads(REGISTRY.read_text(encoding="utf-8"))
    raw["sources"][0]["licence"]["id"] = "CC-BY-NC-4.0"
    with tempfile.TemporaryDirectory() as td:
        bad = Path(td) / "bad.json"
        bad.write_text(json.dumps(raw), encoding="utf-8")
        expect_raises(lambda: SourceRegistry.load(str(bad)), "unapproved licence")

    raw = json.loads(REGISTRY.read_text(encoding="utf-8"))
    raw["sources"][0]["typoField"] = True
    with tempfile.TemporaryDirectory() as td:
        bad = Path(td) / "bad.json"
        bad.write_text(json.dumps(raw), encoding="utf-8")
        expect_raises(lambda: SourceRegistry.load(str(bad)), "unknown fields")

    expect_raises(lambda: registry.get("tatoeba").resolve_source_version(None), "explicit source version")
    assert registry.get("tatoeba").resolve_source_version("2026-09-05") == "2026-09-05"
    return registry


def test_tatoeba(registry: SourceRegistry) -> list[Candidate]:
    rows = list(
        iter_tatoeba(
            str(FIXTURES / "tatoeba"),
            registry.get("tatoeba"),
            "en",
            "es",
            source_version="fixture-1",
        )
    )
    assert len(rows) == 2
    assert rows[0].collection == "everyday"
    assert rows[0].origins[0].context_ref == "tatoeba:100"
    assert rows[0].origins[0].meaning_ref == "tatoeba:101"
    assert rows[0].origins[0].source_version == "fixture-1"
    assert rows[0].origins[0].attribution == {
        "contextContributor": "Alice",
        "meaningContributor": "Beatriz",
    }
    matrix = list(
        iter_tatoeba_matrix(
            str(FIXTURES / "tatoeba"),
            registry.get("tatoeba"),
            {"en", "es"},
            {"en", "es"},
            source_version="fixture-1",
        )
    )
    assert len(merge_candidates(matrix)) == 4
    assert {(row.lang, row.meaning_lang) for row in matrix} == {("en", "es"), ("es", "en")}
    return rows


def test_wikimatrix(registry: SourceRegistry) -> list[Candidate]:
    base = FIXTURES / "wikimatrix"
    rows = list(
        iter_wikimatrix(
            str(base / "en.txt"),
            str(base / "es.txt"),
            registry.get("wikimatrix"),
            "en",
            "es",
            score_path=str(base / "scores.txt"),
            min_score=1.0,
            source_version="v1-fixture",
        )
    )
    assert len(rows) == 3
    assert rows[0].collection == "knowledge"
    assert rows[0].origins[0].alignment_score == 1.42
    merged = merge_candidates(rows)
    assert len(merged) == 2
    duplicate = [row for row in merged if row.context.startswith("The city")][0]
    assert len(duplicate.origins) == 2

    direct = list(
        iter_wikimatrix_tsv(
            str(base / "sample.tsv"),
            registry.get("wikimatrix"),
            "en",
            "es",
            min_score=1.0,
            source_version="v1-fixture",
            tsv_languages=("en", "es"),
        )
    )
    assert [row.to_dict() for row in direct] == [row.to_dict() for row in rows]

    reversed_rows = list(
        iter_wikimatrix_tsv(
            str(base / "sample.tsv"),
            registry.get("wikimatrix"),
            "es",
            "en",
            min_score=1.0,
            source_version="v1-fixture",
            tsv_languages=("en", "es"),
        )
    )
    assert reversed_rows[0].context == "El agua se congela a cero grados Celsius."
    assert reversed_rows[0].meaning == "Water freezes at zero degrees Celsius."
    pair_rows = list(
        iter_wikimatrix_tsv_pair(
            str(base / "sample.tsv"), registry.get("wikimatrix"), "en", "es",
            min_score=1.0, source_version="v1-fixture", max_rows=1,
        )
    )
    assert len(pair_rows) == 2
    assert {(row.lang, row.meaning_lang) for row in pair_rows} == {("en", "es"), ("es", "en")}
    assert infer_wikimatrix_tsv_languages("WikiMatrix.de-en.tsv.gz") == ("de", "en")
    assert infer_wikimatrix_tsv_languages("sample.tsv") is None
    expect_raises(
        lambda: list(
            iter_wikimatrix_tsv(
                str(base / "sample.tsv"),
                registry.get("wikimatrix"),
                "en",
                "es",
                source_version="v1-fixture",
            )
        ),
        "cannot infer WikiMatrix TSV column languages",
    )
    return merged


def test_globalvoices(registry: SourceRegistry) -> list[Candidate]:
    base = FIXTURES / "globalvoices"
    rows = list(
        iter_globalvoices(
            str(base / "en.txt"),
            str(base / "es.txt"),
            str(base / "attribution.jsonl"),
            registry.get("globalvoices"),
            "en",
            "es",
            source_version="v2018q4-fixture",
        )
    )
    assert len(rows) == 2
    assert rows[0].collection == "world"
    assert rows[0].origins[0].attribution["articleUrl"].startswith("https://globalvoices.org/")

    with tempfile.TemporaryDirectory() as td:
        sidecar = Path(td) / "bad.jsonl"
        sidecar.write_text(
            '{"line":1,"articleUrl":"https://globalvoices.org/example/one"}\n', encoding="utf-8"
        )
        expect_raises(
            lambda: list(
                iter_globalvoices(
                    str(base / "en.txt"),
                    str(base / "es.txt"),
                    str(sidecar),
                    registry.get("globalvoices"),
                    "en",
                    "es",
                )
            ),
            "required attribution",
        )

        bad_url = Path(td) / "bad-url.jsonl"
        bad_url.write_text(
            '{"line":1,"articleUrl":"http://globalvoices.org/example/one","contributors":["A"]}\n'
            '{"line":2,"articleUrl":"https://globalvoices.org/example/two","contributors":["B"]}\n',
            encoding="utf-8",
        )
        expect_raises(
            lambda: list(
                iter_globalvoices(
                    str(base / "en.txt"),
                    str(base / "es.txt"),
                    str(bad_url),
                    registry.get("globalvoices"),
                    "en",
                    "es",
                )
            ),
            "must use https://",
        )
    return rows


def test_identity_and_collection_boundary(registry: SourceRegistry) -> None:
    origin_a = Origin("tatoeba", "x", "a", "b")
    origin_b = Origin("tatoeba", "x", "c", "d")
    first = Candidate("everyday", "en", "es", " Same  text ", "Mismo texto", [origin_a])
    second = Candidate("everyday", "en", "es", "same text", "mismo texto", [origin_b])
    assert first.id == second.id
    merged = merge_candidates([first, second])
    assert len(merged) == 1 and len(merged[0].origins) == 2

    knowledge = Candidate("knowledge", "en", "es", "same text", "mismo texto", [origin_b])
    assert knowledge.id != first.id
    assert len(merge_candidates([first, knowledge])) == 2


def test_round_trip(rows: list[Candidate]) -> None:
    with tempfile.TemporaryDirectory() as td:
        path = Path(td) / "candidates.jsonl"
        count = write_jsonl(str(path), rows)
        assert count == len(rows)
        reread = read_jsonl(str(path))
        assert [row.to_dict() for row in reread] == [row.to_dict() for row in rows]

        gzip_path = Path(td) / "candidates.jsonl.gz"
        write_jsonl(str(gzip_path), rows)
        assert [row.to_dict() for row in read_jsonl(str(gzip_path))] == [row.to_dict() for row in rows]

        duplicate_path = Path(td) / "duplicates.jsonl"
        write_jsonl(str(duplicate_path), [rows[0], rows[0]])
        merged_path = Path(td) / "merged.jsonl"
        input_count, output_count = merge_candidate_files(
            [str(path), str(duplicate_path)], str(merged_path)
        )
        assert input_count == len(rows) + 2
        assert output_count == len(rows)
        assert len(read_jsonl(str(merged_path))) == len(rows)


def test_cli(registry: SourceRegistry) -> None:
    with tempfile.TemporaryDirectory() as td:
        td_path = Path(td)
        tatoeba_out = td_path / "tatoeba.jsonl"
        subprocess.run(
            [
                sys.executable,
                str(CLI),
                "tatoeba",
                "--dump-dir",
                str(FIXTURES / "tatoeba"),
                "--learn",
                "en",
                "--meaning",
                "es",
                "--source-version",
                "fixture-1",
                "--out",
                str(tatoeba_out),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        assert len(read_jsonl(str(tatoeba_out))) == 2

        matrix_out = td_path / "tatoeba-matrix.jsonl.gz"
        subprocess.run(
            [
                sys.executable, str(CLI), "tatoeba-matrix",
                "--dump-dir", str(FIXTURES / "tatoeba"),
                "--learn", "en,es", "--meanings", "en,es",
                "--source-version", "fixture-1", "--out", str(matrix_out),
            ],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        assert len(merge_candidates(read_jsonl(str(matrix_out)))) == 4

        wiki_out = td_path / "wikimatrix.jsonl"
        subprocess.run(
            [
                sys.executable,
                str(CLI),
                "wikimatrix",
                "--tsv",
                str(FIXTURES / "wikimatrix" / "sample.tsv"),
                "--tsv-langs",
                "en,es",
                "--learn",
                "es",
                "--meaning",
                "en",
                "--min-score",
                "1.0",
                "--out",
                str(wiki_out),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        wiki_rows = read_jsonl(str(wiki_out))
        assert len(wiki_rows) == 3
        assert wiki_rows[0].context.startswith("El agua")

        pair_out = td_path / "wikimatrix-pair.jsonl.gz"
        subprocess.run(
            [sys.executable, str(CLI), "wikimatrix-pair",
             "--tsv", str(FIXTURES / "wikimatrix" / "sample.tsv"),
             "--first", "en", "--second", "es", "--min-score", "1.0",
             "--max-rows", "1", "--out", str(pair_out)],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        assert len(read_jsonl(str(pair_out))) == 2

        gv_out = td_path / "globalvoices.jsonl"
        subprocess.run(
            [
                sys.executable,
                str(CLI),
                "globalvoices",
                "--learn-file",
                str(FIXTURES / "globalvoices" / "en.txt"),
                "--meaning-file",
                str(FIXTURES / "globalvoices" / "es.txt"),
                "--attribution-file",
                str(FIXTURES / "globalvoices" / "attribution.jsonl"),
                "--learn",
                "en",
                "--meaning",
                "es",
                "--out",
                str(gv_out),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        assert len(read_jsonl(str(gv_out))) == 2

        merged_out = td_path / "merged.jsonl"
        subprocess.run(
            [
                sys.executable,
                str(CLI),
                "merge",
                str(tatoeba_out),
                str(tatoeba_out),
                "--out",
                str(merged_out),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        assert len(read_jsonl(str(merged_out))) == 2


def main() -> int:
    registry = test_registry()
    tatoeba = test_tatoeba(registry)
    wikimatrix = test_wikimatrix(registry)
    globalvoices = test_globalvoices(registry)
    test_identity_and_collection_boundary(registry)
    test_round_trip(tatoeba + wikimatrix + globalvoices)
    test_cli(registry)
    print("Catalogue v2 ingestion contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
