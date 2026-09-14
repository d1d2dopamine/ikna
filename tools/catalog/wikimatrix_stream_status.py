#!/usr/bin/env python3
"""Validate bounded WikiMatrix streaming pipeline exit statuses.

A bounded catalogue reader deliberately closes stdin after ``max_rows`` source
rows. That close propagates upstream as a broken pipe: curl commonly exits 23
and GNU gzip may exit either 141 (SIGPIPE) or 1 while reporting
``stdout: Broken pipe``. Those statuses are only safe when the downstream
Python process exited cleanly and produced the complete requested prefix.
"""
from __future__ import annotations

import argparse
import gzip
from pathlib import Path


def gzip_line_count(path: Path) -> int:
    count = 0
    with gzip.open(path, "rb") as handle:
        for _ in handle:
            count += 1
    return count


def validate(
    out: Path,
    max_rows: int,
    curl_status: int,
    gzip_status: int,
    python_status: int,
) -> tuple[bool, str]:
    if python_status != 0:
        return False, f"downstream Python failed with exit {python_status}"

    if curl_status == 0 and gzip_status == 0:
        return True, "WikiMatrix stream completed normally"

    if max_rows <= 0:
        return False, "upstream failed during an unbounded WikiMatrix stream"

    if curl_status not in (0, 23):
        return False, f"unexpected curl exit {curl_status}"
    if gzip_status not in (0, 1, 141):
        return False, f"unexpected gzip exit {gzip_status}"
    if not out.is_file():
        return False, f"bounded output is missing: {out}"

    try:
        lines = gzip_line_count(out)
    except (OSError, EOFError) as exc:
        return False, f"bounded output is not a complete gzip file: {exc}"

    expected = max_rows * 2
    if lines != expected:
        return False, f"bounded output has {lines} candidates; expected {expected}"

    return (
        True,
        "Accepted intentional bounded WikiMatrix stop "
        f"(curl={curl_status} gzip={gzip_status} python={python_status}; "
        f"{max_rows} source rows / {lines} directional candidates)",
    )


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max-rows", type=int, required=True)
    ap.add_argument("--curl-status", type=int, required=True)
    ap.add_argument("--gzip-status", type=int, required=True)
    ap.add_argument("--python-status", type=int, required=True)
    args = ap.parse_args(argv)

    ok, message = validate(
        args.out,
        args.max_rows,
        args.curl_status,
        args.gzip_status,
        args.python_status,
    )
    print(message)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
