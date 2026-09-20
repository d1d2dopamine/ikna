#!/usr/bin/env python3
"""Validate WikiMatrix streaming pipeline exit statuses.

A score-sorted reader may intentionally close stdin either after ``max_rows``
or when the next alignment score falls below the configured floor. Both closes
propagate upstream as a broken pipe. A reader-status sidecar records the actual
stop reason so those intentional closes can be accepted without hiding a truly
truncated download.
"""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
from typing import Any


def gzip_line_count(path: Path) -> int:
    count = 0
    with gzip.open(path, "rb") as handle:
        for _ in handle:
            count += 1
    return count


def load_reader_status(path: Path | None) -> dict[str, Any] | None:
    if path is None:
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"invalid WikiMatrix reader status: {exc}") from exc
    if not isinstance(value, dict):
        raise ValueError("WikiMatrix reader status must be a JSON object")
    reason = value.get("reason")
    emitted = value.get("emittedSourceRows")
    if reason not in {"eof", "score-threshold", "max-rows"}:
        raise ValueError("WikiMatrix reader status has an unknown reason")
    if not isinstance(emitted, int) or isinstance(emitted, bool) or emitted < 0:
        raise ValueError("WikiMatrix reader status has an invalid emittedSourceRows")
    return {"reason": reason, "emittedSourceRows": emitted}


def validate(
    out: Path,
    max_rows: int,
    curl_status: int,
    gzip_status: int,
    python_status: int,
    reader_status: dict[str, Any] | None = None,
) -> tuple[bool, str]:
    if python_status != 0:
        return False, f"downstream Python failed with exit {python_status}"

    if curl_status == 0 and gzip_status == 0:
        return True, "WikiMatrix stream completed normally"

    if curl_status not in (0, 23):
        return False, f"unexpected curl exit {curl_status}"
    if gzip_status not in (0, 1, 141):
        return False, f"unexpected gzip exit {gzip_status}"
    if not out.is_file():
        return False, f"stream output is missing: {out}"
    if reader_status is None:
        return False, "upstream pipe failed without a reader stop reason"

    try:
        lines = gzip_line_count(out)
    except (OSError, EOFError) as exc:
        return False, f"stream output is not a complete gzip file: {exc}"

    reason = reader_status.get("reason")
    emitted = reader_status.get("emittedSourceRows")
    if reason not in {"eof", "score-threshold", "max-rows"}:
        return False, "reader stop reason is invalid"
    if not isinstance(emitted, int) or isinstance(emitted, bool) or emitted < 0:
        return False, "reader emittedSourceRows is invalid"
    expected_lines = emitted * 2
    if lines != expected_lines:
        return False, f"stream output has {lines} candidates; reader reported {expected_lines}"

    if reason == "eof":
        return False, "upstream failed before a clean WikiMatrix EOF"

    if reason == "max-rows":
        if max_rows <= 0:
            return False, "reader reported max-rows stop for an unbounded stream"
        if emitted != max_rows:
            return False, f"reader stopped at {emitted} source rows; expected max_rows={max_rows}"
        return (
            True,
            "Accepted intentional bounded WikiMatrix stop "
            f"(curl={curl_status} gzip={gzip_status} python={python_status}; "
            f"{emitted} source rows / {lines} directional candidates)",
        )

    if max_rows > 0 and emitted >= max_rows:
        return False, "score-threshold stop cannot occur at or beyond max_rows"
    return (
        True,
        "Accepted intentional WikiMatrix score-threshold stop "
        f"(curl={curl_status} gzip={gzip_status} python={python_status}; "
        f"{emitted} source rows / {lines} directional candidates)",
    )


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max-rows", type=int, required=True)
    ap.add_argument("--curl-status", type=int, required=True)
    ap.add_argument("--gzip-status", type=int, required=True)
    ap.add_argument("--python-status", type=int, required=True)
    ap.add_argument("--reader-status", type=Path)
    args = ap.parse_args(argv)

    try:
        reader_status = load_reader_status(args.reader_status)
    except ValueError as exc:
        print(exc)
        return 1

    ok, message = validate(
        args.out,
        args.max_rows,
        args.curl_status,
        args.gzip_status,
        args.python_status,
        reader_status=reader_status,
    )
    print(message)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
