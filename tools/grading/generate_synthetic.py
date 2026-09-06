#!/usr/bin/env python3
"""Deterministic TEST data. Never an empirical validation or a user's history.

Outcomes come from a deliberately simple latent-ability model, not from the
candidate FSRS parameters being evaluated. The three scenarios expose optional
peeks, the actual required-reveal UI and dirty/missing data respectively.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import random

ROOT = Path(__file__).resolve().parent
BASE_TS = 1_700_000_000_000
SCENARIOS = ("optional", "required", "noise")


def generate(scenario: str) -> list[dict]:
    rng = random.Random(73021)
    records = []
    for i in range(1200):
        card = i % 120
        level = card % 3
        length = (16, 36, 64, 100, 144)[card % 5]
        ability = (card % 11) / 10.0
        fatigue = 0.1 * math.sin(i / 53.0)
        recall_probability = max(0.15, min(0.96, 0.65 + ability * 0.25 - fatigue))
        recalled = rng.random() < recall_probability
        retrieval_cost = max(50, 700 - ability * 380 + rng.gauss(0, 150) + fatigue * 500)
        latency = round(retrieval_cost * math.sqrt(length))
        peek = recalled and rng.random() < (0.20 - ability * 0.10)
        row = dict(
            synthetic=True, id=i + 1, chunkId=f"SYNTHETIC-ONLY-{card:03d}", level=level,
            ts=BASE_TS + i * 1_800_000, rating=3 if recalled else 1,
            inputRating=3 if recalled else 1,
            durationMs=latency + 1200, latencyMs=latency,
            swipeVelocityX=round(rng.uniform(200, 1700) * (1 if recalled else -1), 2),
            peeked=True if scenario == "required" else peek,
            peekSemantics="required_reveal" if scenario == "required" else "optional_peek",
            presentationLength=length, inputMethod="swipe", wasAmnesty=False,
        )
        if scenario == "noise":
            if i % 37 == 0:
                row.update(latencyMs=2_400_000, durationMs=2_401_000, timingDiscardReason="timeout")
            elif i % 41 == 0:
                row.update(timingDiscardReason="focus_lost")
            elif i % 43 == 0:
                row.update(timingDiscardReason="screen_off")
            elif i % 31 == 0:
                row.update(inputMethod="keyboard", latencyMs=None, swipeVelocityX=None, timingDiscardReason="no_swipe")
            elif i % 47 == 0:
                # Synthetically model a legacy row, not a fabricated zero latency.
                for key in ("inputRating", "latencyMs", "swipeVelocityX", "peeked", "peekSemantics", "presentationLength", "inputMethod"):
                    row.pop(key)
        records.append(row)
        if i > 0 and i % 173 == 0:
            records.append(dict(synthetic=True, id=10_000 + i, chunkId=row["chunkId"], level=level,
                                ts=row["ts"] + 1, rating=0, undoOf=row["id"]))
    return records


def serialized(scenario):
    return "".join(json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n" for row in generate(scenario))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify fixtures without rewriting")
    args = parser.parse_args()
    folder = ROOT / "fixtures"
    if not args.check:
        folder.mkdir(parents=True, exist_ok=True)
    for scenario in SCENARIOS:
        path = folder / f"synthetic-{scenario}.jsonl"
        expected = serialized(scenario)
        if args.check:
            assert path.read_text() == expected, f"Fixture differs from seed: {path}"
        else:
            path.write_text(expected)
        rows = [json.loads(line) for line in expected.splitlines()]
        assert all(row["synthetic"] is True and row["chunkId"].startswith("SYNTHETIC-ONLY-") for row in rows)
        assert len({row["id"] for row in rows}) == len(rows)
        assert len({(row["chunkId"], row.get("level", 0), row["ts"]) for row in rows}) == len(rows)
        print(f"{path.name}: {len(rows)} explicitly synthetic rows; seed 73021; reproducible")


if __name__ == "__main__":
    main()
