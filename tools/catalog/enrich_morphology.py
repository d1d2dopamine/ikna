#!/usr/bin/env python3
"""Build a pinned morphology index and enrich Catalogue JSONL cards."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from morphology.manifest import load_manifest
from morphology.model import MORPHOLOGY_POLICY, MORPHOLOGY_RULE_VERSION
from morphology.store import MorphologyResolver, build_store


def build_command(args: argparse.Namespace) -> int:
    manifest = load_manifest(args.manifest)
    stats = build_store(manifest, args.db)
    print("morphology index built")
    print("  ruleVersion: %d" % MORPHOLOGY_RULE_VERSION)
    print("  policy: %s" % MORPHOLOGY_POLICY)
    for key in sorted(stats):
        print("  %s: %s" % (key, stats[key]))
    return 0


def enrich_command(args: argparse.Namespace) -> int:
    stats = Counter()
    with MorphologyResolver(args.db) as resolver:
        with open(args.input, encoding="utf-8") as source, open(args.output, "w", encoding="utf-8", newline="\n") as target:
            for number, line in enumerate(source, start=1):
                line = line.strip()
                if not line:
                    continue
                try:
                    card = json.loads(line)
                    enriched, row_stats = resolver.enrich_card(card, args.lang.lower())
                except Exception as exc:
                    raise ValueError("%s:%d: %s" % (args.input, number, exc)) from exc
                target.write(json.dumps(enriched, ensure_ascii=False, sort_keys=True) + "\n")
                stats.update(row_stats)
        report = {
            "morphologyRuleVersion": MORPHOLOGY_RULE_VERSION,
            "policy": MORPHOLOGY_POLICY,
            "lang": args.lang.lower(),
            "input": str(args.input),
            "output": str(args.output),
            "stats": dict(sorted(stats.items())),
            "datasets": resolver.public_datasets(),
        }
    if args.report:
        Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


def inspect_command(args: argparse.Namespace) -> int:
    with MorphologyResolver(args.db) as resolver:
        token = {"surface": args.surface, "lemma": args.surface.casefold(), "pos": "WORD", "isContent": True}
        card = {"context": args.context or "", "tokens": [token]}
        enriched, stats = resolver.enrich_card(card, args.lang.lower())
        print(json.dumps({"token": enriched["tokens"][0], "stats": dict(stats)}, ensure_ascii=False, indent=2))
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    sub = root.add_subparsers(dest="command", required=True)

    build = sub.add_parser("build-index", help="validate pinned inputs and build a SQLite morphology index")
    build.add_argument("--manifest", required=True)
    build.add_argument("--db", required=True)
    build.set_defaults(func=build_command)

    enrich = sub.add_parser("enrich", help="enrich an existing deck JSONL without changing card identity")
    enrich.add_argument("--db", required=True)
    enrich.add_argument("--lang", required=True)
    enrich.add_argument("--input", required=True)
    enrich.add_argument("--output", required=True)
    enrich.add_argument("--report")
    enrich.set_defaults(func=enrich_command)

    inspect = sub.add_parser("inspect", help="inspect one surface-form lookup")
    inspect.add_argument("--db", required=True)
    inspect.add_argument("--lang", required=True)
    inspect.add_argument("--surface", required=True)
    inspect.add_argument("--context")
    inspect.set_defaults(func=inspect_command)
    return root


def main() -> int:
    args = parser().parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
