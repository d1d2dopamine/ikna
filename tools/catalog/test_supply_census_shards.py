#!/usr/bin/env python3
"""Equivalence checks for the sharded Part 5 supply-census workflow."""
from __future__ import annotations

import csv
import json
import tempfile
from pathlib import Path

from ingest.model import Candidate, Origin, write_jsonl
from supply_census import build_report, parser as monolithic_parser
from supply_census_shards import (
    assemble_report,
    build_rank_counts,
    build_rank_map,
    build_rank_map_from_parts,
    measure_everyday_shard,
    measure_pair_shard,
    write_rank_counts,
    write_ranks,
)


def candidate(
    collection: str,
    lang: str,
    meaning_lang: str,
    context: str,
    meaning: str,
    family: str,
    context_ref: str,
    meaning_ref: str,
    score: float | None = None,
) -> Candidate:
    return Candidate(
        collection=collection,
        lang=lang,
        meaning_lang=meaning_lang,
        context=context,
        meaning=meaning,
        origins=[Origin(family, "fixture-v1", context_ref, meaning_ref, score)],
    )


def wm_pair(first: str, second: str, rows: list[tuple[str, str]]) -> list[Candidate]:
    out: list[Candidate] = []
    for number, (left, right) in enumerate(rows, start=1):
        left_ref = f"wm:{first}-{second}:{number}:left"
        right_ref = f"wm:{first}-{second}:{number}:right"
        out.append(candidate("knowledge", first, second, left, right, "wikimatrix", left_ref, right_ref, 1.2))
        out.append(candidate("knowledge", second, first, right, left, "wikimatrix", right_ref, left_ref, 1.2))
    return out


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="ikna-supply-shards-") as td:
        root = Path(td)
        tatoeba = root / "tatoeba.jsonl.gz"
        wm_en_es = root / "wikimatrix-en-es.jsonl.gz"
        wm_en_ko = root / "wikimatrix-en-ko.jsonl.gz"
        wm_es_ko = root / "wikimatrix-es-ko.jsonl.gz"

        # Shared Tatoeba context refs across meaning languages deliberately test
        # the monolithic context de-duplication used for Everyday ranks.
        write_jsonl(
            str(tatoeba),
            [
                candidate("everyday", "en", "es", "Amber zebra walks slowly today.", "La cebra ambar camina lentamente hoy.", "tatoeba", "tatoeba:1", "tatoeba:2"),
                candidate("everyday", "en", "ko", "Amber zebra walks slowly today.", "호박색 얼룩말이 오늘 천천히 걷습니다.", "tatoeba", "tatoeba:1", "tatoeba:3"),
                candidate("everyday", "es", "en", "La cebra ambar camina lentamente hoy.", "Amber zebra walks slowly today.", "tatoeba", "tatoeba:2", "tatoeba:1"),
                candidate("everyday", "ko", "en", "호박색 얼룩말이 오늘 천천히 걷습니다.", "Amber zebra walks slowly today.", "tatoeba", "tatoeba:3", "tatoeba:1"),
                candidate("everyday", "es", "ko", "La linterna violeta queda cerca.", "보라색 랜턴은 가까이에 있습니다.", "tatoeba", "tatoeba:4", "tatoeba:5"),
                candidate("everyday", "ko", "es", "보라색 랜턴은 가까이에 있습니다.", "La linterna violeta queda cerca.", "tatoeba", "tatoeba:5", "tatoeba:4"),
            ],
        )

        en_es_rows = wm_pair(
            "en", "es",
            [
                ("Quartz velvet lanterns appear nearby.", "Linternas de cuarzo y terciopelo aparecen cerca."),
                ("Zebra quartz patterns remain visible.", "Los patrones de cebra y cuarzo siguen visibles."),
            ],
        )
        # Exact bilingual duplicate with a different source ref: stage_candidates
        # keeps only the first candidate ID, and the rank shard must do the same.
        dup = candidate(
            "knowledge", "en", "es",
            en_es_rows[0].context, en_es_rows[0].meaning, "wikimatrix",
            "wm:en-es:99:left", "wm:en-es:99:right", 1.19,
        )
        write_jsonl(str(wm_en_es), [*en_es_rows, dup])
        write_jsonl(
            str(wm_en_ko),
            wm_pair(
                "en", "ko",
                [
                    ("Velvet xylophone signals remain stable.", "벨벳 실로폰 신호는 안정적으로 유지됩니다."),
                    ("Quartz xylophone archives stay available.", "석영 실로폰 기록은 계속 사용할 수 있습니다."),
                ],
            ),
        )
        write_jsonl(
            str(wm_es_ko),
            wm_pair(
                "es", "ko",
                [
                    ("El cuarzo violeta permanece estable.", "보라색 석영은 안정적으로 유지됩니다."),
                    ("La cebra observa archivos cercanos.", "얼룩말은 가까운 기록을 관찰합니다."),
                ],
            ),
        )

        inventory = root / "inventory.tsv"
        with inventory.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
            writer.writerow(["first", "second", "status", "retained_rows", "acquisition_capped", "url"])
            writer.writerow(["en", "es", "available", "2", "false", "https://example.invalid/en-es.gz"])
            writer.writerow(["en", "ko", "available", "2", "true", "https://example.invalid/en-ko.gz"])
            writer.writerow(["es", "ko", "available", "2", "false", "https://example.invalid/es-ko.gz"])

        args = monolithic_parser().parse_args(
            [
                "--candidates", str(tatoeba), str(wm_en_es), str(wm_en_ko), str(wm_es_ko),
                "--json", str(root / "mono.json"),
                "--markdown", str(root / "mono.md"),
                "--learn", "en,es,ko",
                "--meanings", "en,es,ko",
                "--max-deck", "2",
                "--min-deck", "1",
                "--function-top", "0",
                "--wikimatrix-inventory", str(inventory),
                "--staging", str(root / "mono.sqlite3"),
            ]
        )
        monolithic = build_report(args)

        rank_inputs = {
            "en": [wm_en_es, wm_en_ko],
            "es": [wm_en_es, wm_es_ko],
            "ko": [wm_en_ko, wm_es_ko],
        }
        rank_paths: dict[str, Path] = {}
        for lang, paths in rank_inputs.items():
            ranks = build_rank_map([str(path) for path in paths], "knowledge", lang)
            part_paths: list[str] = []
            for path in paths:
                first, second = path.name.removeprefix("wikimatrix-").removesuffix(".jsonl.gz").split("-")
                meaning = second if first == lang else first
                counts, found_meaning = build_rank_counts(
                    str(path), "knowledge", lang, expected_meaning=meaning
                )
                if found_meaning != meaning:
                    raise AssertionError(f"rank count shard missed {lang}->{meaning}")
                part_path = root / f"rank-counts-{lang}-{meaning}.tsv.gz"
                write_rank_counts(part_path, "knowledge", lang, meaning, counts)
                part_paths.append(str(part_path))
            from_parts = build_rank_map_from_parts(part_paths, "knowledge", lang)
            if from_parts != ranks:
                raise AssertionError(f"rank-count shards changed {lang} rank order")
            rank_path = root / f"ranks-{lang}.tsv.gz"
            write_ranks(rank_path, "knowledge", lang, from_parts)
            rank_paths[lang] = rank_path

        everyday = measure_everyday_shard(
            str(tatoeba), root / "everyday.sqlite3", ["en", "es", "ko"], ["en", "es", "ko"], 2, 1, 0
        )
        everyday_path = root / "everyday.json"
        everyday_path.write_text(json.dumps(everyday), encoding="utf-8")

        knowledge_paths: list[str] = []
        for first, second, path in (
            ("en", "es", wm_en_es),
            ("en", "ko", wm_en_ko),
            ("es", "ko", wm_es_ko),
        ):
            pair_inventory = root / f"inventory-{first}-{second}.tsv"
            with inventory.open(encoding="utf-8", newline="") as source:
                rows = list(csv.DictReader(source, delimiter="\t"))
            row = next(item for item in rows if item["first"] == first and item["second"] == second)
            with pair_inventory.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(row), delimiter="\t", lineterminator="\n")
                writer.writeheader()
                writer.writerow(row)
            shard = measure_pair_shard(
                str(path), root / f"{first}-{second}.sqlite3", first, second,
                root, str(pair_inventory), ["en", "es", "ko"], ["en", "es", "ko"], 2, 1, 0,
            )
            shard_path = root / f"shard-{first}-{second}.json"
            shard_path.write_text(json.dumps(shard), encoding="utf-8")
            knowledge_paths.append(str(shard_path))

        sharded = assemble_report(
            str(everyday_path), knowledge_paths, ["en", "es", "ko"], ["en", "es", "ko"], 2, 1, 0
        )
        if sharded != monolithic:
            (root / "monolithic.json").write_text(json.dumps(monolithic, ensure_ascii=False, indent=2), encoding="utf-8")
            (root / "sharded.json").write_text(json.dumps(sharded, ensure_ascii=False, indent=2), encoding="utf-8")
            raise AssertionError("sharded census differs from monolithic census")

        # Custom workflow inputs may be asymmetric. The old monolithic job
        # staged every physical WikiMatrix pair in the learn/meaning union even
        # when a pair produced no requested directed row. Preserve those stage
        # diagnostics as well as the measured rows.
        asym_args = monolithic_parser().parse_args(
            [
                "--candidates", str(tatoeba), str(wm_en_es), str(wm_en_ko), str(wm_es_ko),
                "--json", str(root / "asym-mono.json"),
                "--markdown", str(root / "asym-mono.md"),
                "--learn", "en",
                "--meanings", "es,ko",
                "--max-deck", "2",
                "--min-deck", "1",
                "--function-top", "0",
                "--wikimatrix-inventory", str(inventory),
                "--staging", str(root / "asym-mono.sqlite3"),
            ]
        )
        asym_monolithic = build_report(asym_args)
        asym_everyday = measure_everyday_shard(
            str(tatoeba), root / "asym-everyday.sqlite3", ["en"], ["es", "ko"], 2, 1, 0
        )
        asym_everyday_path = root / "asym-everyday.json"
        asym_everyday_path.write_text(json.dumps(asym_everyday), encoding="utf-8")
        asym_knowledge_paths: list[str] = []
        for first, second, path in (
            ("en", "es", wm_en_es),
            ("en", "ko", wm_en_ko),
            ("es", "ko", wm_es_ko),
        ):
            pair_inventory = root / f"inventory-{first}-{second}.tsv"
            shard = measure_pair_shard(
                str(path), root / f"asym-{first}-{second}.sqlite3", first, second,
                root, str(pair_inventory), ["en"], ["es", "ko"], 2, 1, 0,
            )
            shard_path = root / f"asym-shard-{first}-{second}.json"
            shard_path.write_text(json.dumps(shard), encoding="utf-8")
            asym_knowledge_paths.append(str(shard_path))
        asym_sharded = assemble_report(
            str(asym_everyday_path), asym_knowledge_paths, ["en"], ["es", "ko"], 2, 1, 0
        )
        if asym_sharded != asym_monolithic:
            raise AssertionError("asymmetric sharded census differs from monolithic census")

    print("Catalogue v2 sharded supply census equivalence: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
