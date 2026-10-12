#!/usr/bin/env python3
"""Bounded non-GUI regression against a packaged ikna launcher and disposable data.

Usage: python3 check_packaged_profiles.py LAUNCHER NEW_SANDBOX OUTPUT_JSON
Refuses any pre-existing sandbox. Never touches the ordinary learner home.
"""
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys

launcher, home, report = (Path(value).resolve() for value in sys.argv[1:])
if not launcher.is_file():
    raise SystemExit("Packaged launcher is missing")
if home.exists():
    raise SystemExit("Refusing a pre-existing sandbox")
home.mkdir(parents=True)
environment = dict(os.environ, IKNA_SELFTEST_HOME=str(home))
rows = []


def run(label):
    result = subprocess.run([str(launcher), "--selftest"], env=environment,
                            text=True, capture_output=True, timeout=45)
    assert result.returncode == 0, (label, result.stdout, result.stderr)
    assert "selftest: ok" in result.stdout, (label, result.stdout)
    return result.stdout


def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inventory(path):
    with sqlite3.connect(path.as_uri() + "?mode=ro", uri=True) as connection:
        return {name: connection.execute("SELECT COUNT(*) FROM " + name).fetchone()[0]
                for name in ("packs", "chunks", "cards", "reviews")}


real_output = run("REAL before")
real_files = [home / "ikna.db", home / "ikna-settings.preferences_pb"]
real_before = {path.name: fingerprint(path) for path in real_files}
real_inventory = inventory(home / "ikna.db")
rows.append({"profile": "REAL", "phase": "before", "inventory": real_inventory,
             "output": real_output})
for scenario in ("empty", "early-history", "mature-history", "browse-ready",
                 "rich-statistics", "return-after-break"):
    (home / "ikna-data-profile").write_text("DEVELOPER", encoding="utf-8")
    request = home / "ikna-developer-scenario-request"
    request.write_text(scenario, encoding="utf-8")
    output = run(scenario)
    assert not request.exists(), "Successful DEV startup must consume its queued request"
    current = inventory(home / "ikna-developer.db")
    if scenario == "empty":
        assert all(value == 0 for value in current.values()), current
    else:
        assert all(value > 0 for value in current.values()), current
    assert real_before == {path.name: fingerprint(path) for path in real_files}, scenario
    rows.append({"profile": "DEVELOPER", "scenario": scenario, "inventory": current,
                 "request_consumed": True, "real_files_byte_identical": True, "output": output})
    if scenario == "empty":
        output = run("EMPTY second startup without a request")
        assert inventory(home / "ikna-developer.db") == current
        rows.append({"profile": "DEVELOPER", "phase": "empty-reopen", "inventory": current,
                     "output": output})
# A corrupt pending request must fail visibly and remain available for diagnosis.
request = home / "ikna-developer-scenario-request"
request.write_text("invalid-scenario", encoding="utf-8")
dev_before_failure = inventory(home / "ikna-developer.db")
failed = subprocess.run([str(launcher), "--selftest"], env=environment,
                        text=True, capture_output=True, timeout=45)
assert failed.returncode == 1, failed
assert "Invalid pending developer scenario" in failed.stderr, failed.stderr
assert request.read_text(encoding="utf-8") == "invalid-scenario"
assert inventory(home / "ikna-developer.db") == dev_before_failure
assert real_before == {path.name: fingerprint(path) for path in real_files}
rows.append({"profile": "DEVELOPER", "phase": "invalid-request", "exit_code": 1,
             "request_retained": True, "dev_inventory_unchanged": True,
             "real_files_byte_identical": True, "expected_error": "Invalid pending developer scenario"})
request.write_text("empty", encoding="utf-8")
output = run("recover failed request as EMPTY")
assert not request.exists()
assert all(value == 0 for value in inventory(home / "ikna-developer.db").values())
assert real_before == {path.name: fingerprint(path) for path in real_files}
rows.append({"profile": "DEVELOPER", "phase": "request-recovery", "scenario": "empty",
             "output": output, "request_consumed": True, "real_files_byte_identical": True})
(home / "ikna-data-profile").write_text("REAL", encoding="utf-8")
output = run("REAL after")
assert real_inventory == inventory(home / "ikna.db")
rows.append({"profile": "REAL", "phase": "after", "inventory": real_inventory,
             "output": output})
report.write_text(json.dumps({"result": "PASS", "processes": len(rows),
    "scope": "Packaged --selftest startup, database/settings and scenario isolation; no GUI or native restart handoff",
    "real_before_sha256": real_before, "runs": rows}, indent=2) + "\n", encoding="utf-8")
print(f"PASS: {len(rows)} packaged processes; 6 DEV scenarios, empty reopen, failed request/recovery, REAL isolation/return")
