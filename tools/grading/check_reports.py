#!/usr/bin/env python3
"""Assert mechanics of CI reports, never require or proclaim synthetic benefit."""
from pathlib import Path
import math
import sys

root = Path(sys.argv[1])
reports = {}
for scenario in ("optional", "required", "noise"):
    path = root / f"{scenario}.txt"
    report = dict(line.split("=", 1) for line in path.read_text().splitlines() if "=" in line)
    assert report["source"] == "synthetic", "CI must never publish personal history metrics"
    assert report["automaticEnable"] == "false"
    assert report["warrantsRealWorldReview"] == "false"
    assert int(report["scoredAnswers"]) > 0
    assert int(report["heldOutAnswers"]) > 0
    for key in ("binaryBrier", "derivedBrier", "heldOutBinaryBrier", "heldOutDerivedBrier"):
        value = float(report[key])
        assert math.isfinite(value) and 0 <= value <= 1, (scenario, key, value)
    for key in ("binaryLogLoss", "derivedLogLoss"):
        value = float(report[key])
        assert math.isfinite(value) and value >= 0, (scenario, key, value)
    reports[scenario] = report
assert int(reports["optional"]["derivedHard"]) > 0
assert int(reports["optional"]["derivedEasy"]) > 0
assert int(reports["required"]["derivedEasy"]) == 0, "Required reveal must not invent no-peek evidence"
assert int(reports["noise"]["discardedTimings"]) > int(reports["optional"]["discardedTimings"])
print("Production Kotlin replay reports pass structural checks; no empirical benefit claim.")
