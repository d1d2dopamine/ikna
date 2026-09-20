#!/usr/bin/env python3
"""Contracts for WikiMatrix stream status validation."""
from __future__ import annotations

import gzip
import json
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from wikimatrix_stream_status import load_reader_status, validate


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

        max_stop = {"reason": "max-rows", "emittedSourceRows": 50}
        threshold_stop = {"reason": "score-threshold", "emittedSourceRows": 49}
        eof_stop = {"reason": "eof", "emittedSourceRows": 49}

        ok, _ = validate(complete, 50, 23, 1, 0, max_stop)
        assert ok, "GNU gzip broken-pipe exit 1 must be accepted after a complete bounded prefix"
        ok, _ = validate(complete, 50, 23, 141, 0, max_stop)
        assert ok, "SIGPIPE form must remain accepted"
        ok, _ = validate(complete, 50, 0, 0, 0, max_stop)
        assert ok, "normal stream completion must be accepted"

        ok, message = validate(short, 50, 23, 1, 0, threshold_stop)
        assert ok and "score-threshold" in message, "a score-floor stop below max_rows must be accepted"
        ok, _ = validate(short, 0, 23, 1, 0, threshold_stop)
        assert ok, "score-floor stops are also intentional for an otherwise unbounded scan"

        ok, _ = validate(short, 50, 23, 1, 0, eof_stop)
        assert not ok, "a short EOF must not hide an upstream failure"
        ok, _ = validate(short, 50, 23, 1, 0, None)
        assert not ok, "broken pipes need an explicit reader stop reason"
        ok, _ = validate(complete, 50, 18, 1, 0, max_stop)
        assert not ok, "unrelated curl failures must remain failures"
        ok, _ = validate(complete, 50, 23, 1, 2, max_stop)
        assert not ok, "downstream Python failures must remain failures"
        ok, _ = validate(short, 50, 23, 1, 0, {"reason": "max-rows", "emittedSourceRows": 49})
        assert not ok, "max-rows status must exactly match the requested bound"
        ok, _ = validate(short, 49, 23, 1, 0, threshold_stop)
        assert not ok, "threshold status cannot claim a stop at the max_rows boundary"

        status_path = root / "status.json"
        status_path.write_text(json.dumps(threshold_stop), encoding="utf-8")
        assert load_reader_status(status_path) == threshold_stop
        status_path.write_text('{"reason":"mystery","emittedSourceRows":49}', encoding="utf-8")
        try:
            load_reader_status(status_path)
        except ValueError:
            pass
        else:
            raise AssertionError("unknown reader status reasons must fail closed")

    print("WikiMatrix stream status contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
