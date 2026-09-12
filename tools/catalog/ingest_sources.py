#!/usr/bin/env python3
"""Normalize local corpus dumps into Catalogue v2 ingestion candidates.

This command does no downloading and builds no learner deck. It is the boundary
between source-specific dump formats and the source-independent Catalogue v2
pipeline planned for 0.11.
"""

from __future__ import annotations

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from ingest.adapters import (
    infer_wikimatrix_tsv_languages,
    iter_globalvoices,
    iter_tatoeba,
    iter_wikimatrix,
    iter_wikimatrix_tsv,
)
from ingest.model import merge_candidate_files, write_jsonl
from ingest.registry import SourceRegistry

DEFAULT_REGISTRY = os.path.join(HERE, "sources", "catalogue-v2-sources.json")


def _language_pair(value: str) -> tuple[str, str]:
    parts = tuple(part.strip().lower() for part in value.split(","))
    if len(parts) != 2 or not all(parts):
        raise argparse.ArgumentTypeError("expected two comma-separated language codes, e.g. en,fr")
    return parts[0], parts[1]


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    root.add_argument("--registry", default=DEFAULT_REGISTRY)
    sub = root.add_subparsers(dest="command", required=True)

    sub.add_parser("validate-registry")

    t = sub.add_parser("tatoeba")
    t.add_argument("--dump-dir", required=True)
    t.add_argument("--learn", required=True)
    t.add_argument("--meaning", required=True)
    t.add_argument("--out", required=True)
    t.add_argument(
        "--source-version",
        required=True,
        help="immutable weekly-export date or other pinned source version, e.g. 2026-09-05",
    )

    w = sub.add_parser("wikimatrix")
    source = w.add_mutually_exclusive_group(required=True)
    source.add_argument("--tsv", help="upstream WikiMatrix score<TAB>sentence<TAB>sentence TSV or .gz")
    source.add_argument("--learn-file", help="first file of an already split aligned pair")
    w.add_argument("--meaning-file", help="second file when --learn-file is used")
    w.add_argument("--score-file")
    w.add_argument("--min-score", type=float)
    w.add_argument("--learn", required=True)
    w.add_argument("--meaning", required=True)
    w.add_argument("--out", required=True)
    w.add_argument("--source-version")
    w.add_argument(
        "--tsv-langs",
        type=_language_pair,
        help="physical sentence-column languages, e.g. de,en; inferred from WikiMatrix.de-en.tsv.gz when omitted",
    )

    g = sub.add_parser("globalvoices")
    g.add_argument("--learn-file", required=True)
    g.add_argument("--meaning-file", required=True)
    g.add_argument("--attribution-file", required=True)
    g.add_argument("--learn", required=True)
    g.add_argument("--meaning", required=True)
    g.add_argument("--out", required=True)
    g.add_argument("--source-version")

    m = sub.add_parser("merge")
    m.add_argument("inputs", nargs="+")
    m.add_argument("--out", required=True)
    m.add_argument("--db", help="optional persistent SQLite work file for large runs")
    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    registry = SourceRegistry.load(args.registry)

    if args.command == "validate-registry":
        for source_id in sorted(registry.policies):
            p = registry.policies[source_id]
            suffix = " + record attribution" if p.required_record_attribution else ""
            explicit = ", explicit source version" if p.requires_explicit_source_version else ""
            print("%s: %s, %s%s%s" % (source_id, p.licence_id, p.publication_status, suffix, explicit))
        return 0

    if args.command == "merge":
        input_count, output_count = merge_candidate_files(args.inputs, args.out, db_path=args.db)
        print("%d input candidates -> %d exact candidates" % (input_count, output_count))
        return 0

    source_id = {
        "tatoeba": "tatoeba",
        "wikimatrix": "wikimatrix",
        "globalvoices": "globalvoices",
    }[args.command]
    policy = registry.get(source_id)

    if args.command == "tatoeba":
        records = iter_tatoeba(args.dump_dir, policy, args.learn, args.meaning, source_version=args.source_version)
    elif args.command == "wikimatrix":
        if args.tsv:
            if args.meaning_file or args.score_file:
                parser().error("--tsv cannot be combined with --meaning-file or --score-file")
            physical = args.tsv_langs or infer_wikimatrix_tsv_languages(args.tsv)
            if physical is None:
                parser().error(
                    "cannot infer WikiMatrix column languages from the filename; pass --tsv-langs en,fr"
                )
            records = iter_wikimatrix_tsv(
                args.tsv,
                policy,
                args.learn,
                args.meaning,
                min_score=args.min_score,
                source_version=args.source_version,
                tsv_languages=physical,
            )
        else:
            if args.tsv_langs:
                parser().error("--tsv-langs is only valid with --tsv")
            if not args.meaning_file:
                parser().error("--meaning-file is required with --learn-file")
            records = iter_wikimatrix(
                args.learn_file,
                args.meaning_file,
                policy,
                args.learn,
                args.meaning,
                score_path=args.score_file,
                min_score=args.min_score,
                source_version=args.source_version,
            )
    else:
        records = iter_globalvoices(
            args.learn_file,
            args.meaning_file,
            args.attribution_file,
            policy,
            args.learn,
            args.meaning,
            source_version=args.source_version,
        )

    count = write_jsonl(args.out, records)
    print("wrote %d %s candidates to %s" % (count, source_id, args.out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
