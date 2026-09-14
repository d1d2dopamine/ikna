#!/usr/bin/env python3
"""Extract per-line OPUS document identity for 1:1 XCES sentence alignments.

The output intentionally contains document references only. Article URLs and
credits are resolved separately by ``globalvoices_attribution.py`` so document
identity and web attribution cannot be silently conflated.
"""
from __future__ import annotations

import argparse
import gzip
import json
import xml.etree.ElementTree as ET
from pathlib import Path


def _open(path: str):
    return gzip.open(path, "rb") if path.endswith(".gz") else open(path, "rb")


def extract(path: str, context_side: str = "from", context_lang: str | None = None) -> tuple[list[dict], dict]:
    if context_side not in {"from", "to", "auto"}:
        raise ValueError("context side must be from, to, or auto")
    rows = []
    groups = 0
    skipped = 0
    with _open(path) as handle:
        for _event, group in ET.iterparse(handle, events=("end",)):
            if group.tag.rsplit("}", 1)[-1] != "linkGrp":
                continue
            from_doc = group.attrib.get("fromDoc")
            to_doc = group.attrib.get("toDoc")
            if not from_doc or not to_doc:
                group.clear()
                continue
            groups += 1
            side = context_side
            if side == "auto":
                if not context_lang:
                    raise ValueError("context_lang is required when context_side=auto")
                left = from_doc.replace("\\", "/").lstrip("./")
                right = to_doc.replace("\\", "/").lstrip("./")
                left_lang = left.split("/", 1)[0].lower()
                right_lang = right.split("/", 1)[0].lower()
                if left_lang == context_lang.lower() and right_lang != context_lang.lower():
                    side = "from"
                elif right_lang == context_lang.lower() and left_lang != context_lang.lower():
                    side = "to"
                else:
                    raise ValueError(f"cannot infer context side for {from_doc} / {to_doc} and language {context_lang}")
            for link in list(group):
                if link.tag.rsplit("}", 1)[-1] != "link":
                    continue
                xtargets = str(link.attrib.get("xtargets") or "").strip()
                parts = [part.strip() for part in xtargets.split(";")]
                # OPUS Moses output keeps every non-empty aligned link as one
                # text row, including 1:n links. Document identity is link-group
                # level, so sentence cardinality does not weaken attribution.
                if len(parts) != 2 or not parts[0] or not parts[1]:
                    skipped += 1
                    continue
                rows.append({
                    "line": len(rows) + 1,
                    "contextDocument": from_doc if side == "from" else to_doc,
                    "meaningDocument": to_doc if side == "from" else from_doc,
                    "contextSentenceIds": parts[0].split() if side == "from" else parts[1].split(),
                    "meaningSentenceIds": parts[1].split() if side == "from" else parts[0].split(),
                })
            group.clear()
    return rows, {"linkGroups": groups, "alignedRows": len(rows), "emptySideSkipped": skipped}

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--xces", required=True)
    ap.add_argument("--context-side", choices=["from","to","auto"], default="from")
    ap.add_argument("--context-lang")
    ap.add_argument("--out", required=True)
    ap.add_argument("--expected-lines", type=int)
    args = ap.parse_args(argv)
    rows, stats = extract(args.xces, args.context_side, args.context_lang)
    if args.expected_lines is not None and len(rows) != args.expected_lines:
        raise SystemExit(f"XCES non-empty aligned rows ({len(rows)}) do not match expected aligned text lines ({args.expected_lines}); refuse line-number attribution")
    with open(args.out, "w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    print(json.dumps(stats, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
