#!/usr/bin/env python3
"""Assert CI report mechanics without proclaiming synthetic benefit."""
from pathlib import Path
import math
import sys

root = Path(sys.argv[1])
reports = {}
for scenario in ("verified", "immature", "noise"):
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
assert int(reports["verified"]["derivedHard"]) > 0
assert int(reports["verified"]["derivedEasy"]) > 0
assert int(reports["immature"]["derivedEasy"]) == 0, "Immature cards must stay GOOD when fast"
assert int(reports["noise"]["discardedTimings"]) > int(reports["verified"]["discardedTimings"])
print("Production Kotlin replay reports pass structural checks; no empirical benefit claim.")
