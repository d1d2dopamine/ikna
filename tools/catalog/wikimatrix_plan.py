#!/usr/bin/env python3
"""Print deterministic WikiMatrix acquisition plans used by Catalogue v2."""
from __future__ import annotations

import argparse
from itertools import combinations

BASE = "https://dl.fbaipublicfiles.com/laser/WikiMatrix/v1"


def planned_pairs(langs: list[str], hub: str, all_direct: bool) -> list[tuple[str, str]]:
    unique = sorted(set(langs))
    if all_direct:
        return list(combinations(unique, 2))
    if hub not in unique:
        raise ValueError("hub language %s is not in --langs" % hub)
    return [tuple(sorted((hub, other))) for other in unique if other != hub]


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--langs", required=True)
    ap.add_argument("--hub", default="en")
    ap.add_argument(
        "--all-direct",
        action="store_true",
        help="emit every unordered direct pair among --langs instead of one hub star",
    )
    args = ap.parse_args(argv)
    langs = [x.strip().lower() for x in args.langs.split(",") if x.strip()]
    hub = args.hub.strip().lower()
    try:
        pairs = planned_pairs(langs, hub, args.all_direct)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc
    for first, second in pairs:
        name = "WikiMatrix.%s-%s.tsv.gz" % (first, second)
        print("%s\t%s\t%s/%s" % (first, second, BASE, name))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
