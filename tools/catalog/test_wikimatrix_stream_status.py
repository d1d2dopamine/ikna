#!/usr/bin/env python3
"""Contracts for bounded WikiMatrix stream status validation."""
from __future__ import annotations

import gzip
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from wikimatrix_stream_status import validate


def write_lines(path: Path, count: int) -> None:
    with gzip.open(path, "wt", encoding="utf-8", newline="\n") as handle:
        for index in range(count):
            handle.write(f'{{"n":{index}}}\n')


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="ikna-wikimatrix-stream-") as td:
        root = Path(td)
        complete = root / "complete.jsonl.gz"
        short = root / "short.jsonl.gz"
        write_lines(complete, 100)
        write_lines(short, 98)

        ok, _ = validate(complete, 50, 23, 1, 0)
        assert ok, "GNU gzip broken-pipe exit 1 must be accepted after a complete bounded prefix"
        ok, _ = validate(complete, 50, 23, 141, 0)
        assert ok, "SIGPIPE form must remain accepted"
        ok, _ = validate(complete, 50, 0, 0, 0)
        assert ok, "normal stream completion must be accepted"

        ok, _ = validate(short, 50, 23, 1, 0)
        assert not ok, "a short prefix must not hide an upstream failure"
        ok, _ = validate(complete, 0, 23, 1, 0)
        assert not ok, "broken pipes are not expected for unbounded reads"
        ok, _ = validate(complete, 50, 18, 1, 0)
        assert not ok, "unrelated curl failures must remain failures"
        ok, _ = validate(complete, 50, 23, 1, 2)
        assert not ok, "downstream Python failures must remain failures"

    print("WikiMatrix bounded stream status contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
