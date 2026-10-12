#!/usr/bin/env python3
"""Linux resource supervisor for the fixed, non-publishing allocation diagnostic.

Pass guard options, then -- and allocation_diagnostic.py arguments. No arbitrary
command is accepted. Partial scratch/output is preserved for inspection, never
claimed to be resumable. Sampled limits can overshoot between polls; RLIMIT_AS
is a separate hard virtual-address-space ceiling, not an RSS guarantee.
"""
from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

from selection_profile_guard import (GIB, GuardTerminated, atomic_json, existing_parent,
                                     handle_sigterm, parent_death_guard, resource_sample,
                                     terminate_owned, violation)


HERE = Path(__file__).resolve().parent
INPUT_OPTIONS = ("pool", "pool-report", "selection-report", "registry")
OUTPUT_OPTIONS = ("scratch", "out")


def child_routes(argv):
    """One value per known option; no abbreviations, duplicate flags or commands."""
    allowed = {"--" + value for value in INPUT_OPTIONS + OUTPUT_OPTIONS}
    if len(argv) % 2 or any(argv[index] not in allowed for index in range(0, len(argv), 2)):
        raise ValueError("child arguments must be known allocation options followed by their paths")
    if len(set(argv[::2])) != len(argv[::2]):
        raise ValueError("duplicate allocation option")
    routes = {key[2:]: Path(value).resolve() for key, value in zip(argv[::2], argv[1::2])}
    if set(routes) - {"registry"} != set(INPUT_OPTIONS + OUTPUT_OPTIONS) - {"registry"}:
        raise ValueError("pool, pool-report, selection-report, scratch and out are required")
    routes.setdefault("registry", HERE / "sources" / "catalogue-v2-sources.json")
    return routes


def validate(args):
    if not sys.platform.startswith("linux"):
        raise ValueError("resource guard requires Linux /proc and RLIMIT_AS")
    import resource
    if not hasattr(resource, "RLIMIT_AS"):
        raise ValueError("RLIMIT_AS is unavailable")
    for name in ("max_rss_bytes", "address_space_bytes", "min_available_bytes", "min_disk_free_bytes"):
        if type(getattr(args, name)) is not int or getattr(args, name) <= 0:
            raise ValueError("resource byte limits must be positive integers")
    if args.address_space_bytes <= args.max_rss_bytes:
        raise ValueError("address-space ceiling must exceed RSS limit")
    if not math.isfinite(args.poll_seconds) or not 0 < args.poll_seconds <= 10:
        raise ValueError("poll seconds must be finite and in (0, 10]")
    if not math.isfinite(args.max_wall_seconds) or args.max_wall_seconds <= 0:
        raise ValueError("wall seconds must be finite and positive")
    hard = resource.getrlimit(resource.RLIMIT_AS)[1]
    if hard != resource.RLIM_INFINITY and hard < args.address_space_bytes:
        raise ValueError("existing hard address-space ceiling is below requested limit")
    if args.allocation_args[:1] != ["--"]:
        raise ValueError("separate guard and allocation arguments with --")
    routes = child_routes(args.allocation_args[1:])
    inputs = [routes[key] for key in INPUT_OPTIONS]
    if any(not path.is_file() for path in inputs):
        raise ValueError("all allocation inputs must be existing regular files")
    if len(set(inputs)) != len(inputs) or any(a.samefile(b) for index, a in enumerate(inputs)
                                             for b in inputs[index + 1:]):
        raise ValueError("allocation inputs must not alias each other")
    evidence_dir = Path(args.evidence_dir).resolve()
    outputs = [evidence_dir, *(routes[key] for key in OUTPUT_OPTIONS)]
    # Resolve symlink parents as well as existing final paths. Dangling final
    # symlinks are refused explicitly, rather than used as fresh destinations.
    raw_outputs = [Path(args.evidence_dir)] + [Path(args.allocation_args[index + 1])
                   for index in range(1, len(args.allocation_args), 2)
                   if args.allocation_args[index] in {"--scratch", "--out"}]
    if any(path.exists() for path in outputs) or any(path.is_symlink() for path in raw_outputs):
        raise ValueError("evidence, scratch and output directories must be new")
    for index, first in enumerate(outputs):
        if any(first == second or first in second.parents or second in first.parents
               for second in outputs[index + 1:]):
            raise ValueError("evidence, scratch and output directories must be disjoint")
        if any(first == path or first in path.parents or path in first.parents for path in inputs):
            raise ValueError("allocation outputs must not overlap input files")
    parents = [existing_parent(path) for path in outputs]
    if any(not path.is_dir() for path in parents):
        raise ValueError("output ancestors must be directories")
    if len({path.stat().st_dev for path in parents}) != 1:
        raise ValueError("evidence, scratch and output must share the monitored filesystem")
    routed = [part for key in INPUT_OPTIONS + OUTPUT_OPTIONS for part in ("--" + key, str(routes[key]))]
    return evidence_dir, routes, routed


def _record_sample(evidence, sample):
    evidence["sampleCount"] += 1
    evidence["latestSample"] = sample
    for key, value, operation in (("sampledPeakRssBytes", sample["rssBytes"], max),
                                  ("minimumAvailableBytes", sample["availableBytes"], min),
                                  ("minimumDiskFreeBytes", sample["diskFreeBytes"], min)):
        old = evidence[key]
        evidence[key] = value if old is None else operation(old, value)


def supervise(args):
    evidence_dir, routes, allocation_args = validate(args)
    evidence_dir.mkdir(parents=True)
    temporary_dir = evidence_dir / "tmp"
    temporary_dir.mkdir()
    path = evidence_dir / "RESOURCE.json"
    evidence = {"schemaVersion": 1, "tool": "allocation_diagnostic.py", "status": "preflight",
                "publicationSafe": False, "resumable": False, "partialStatePreserved": True,
                "childPid": None, "exitCode": None, "stopReason": None, "sampleCount": 0,
                "sampledPeakRssBytes": None, "minimumAvailableBytes": None,
                "minimumDiskFreeBytes": None, "elapsedSeconds": 0,
                "limits": {key: getattr(args, key) for key in
                           ("max_rss_bytes", "address_space_bytes", "min_available_bytes",
                            "min_disk_free_bytes", "poll_seconds", "max_wall_seconds")},
                "allocationArgs": allocation_args, "temporaryDirectory": str(temporary_dir),
                "temporaryDirectoryContract": "child TMPDIR/TMP/TEMP and SQLITE_TMPDIR use the monitored filesystem",
                "limitations": ["sampled thresholds and wall timeout can overshoot between polls",
                                "RLIMIT_AS limits virtual address space, not RSS or total host use",
                                "unavailable cgroup visibility leaves hidden memory limits unknown",
                                "partial scratch/output is preserved for inspection, not resumption",
                                "zero child exit is a diagnostic result, not selection-policy or publication approval",
                                "SIGKILL leaves incomplete evidence; kernel parent-death signal terminates the child, its adoptive parent must reap it"]}
    child = None
    started = time.monotonic()
    prior_sigterm = signal.signal(signal.SIGTERM, handle_sigterm)
    try:
        initial = resource_sample(os.getpid(), evidence_dir)
        evidence["preflight"] = initial
        reason = violation(initial, args)
        if reason:
            evidence.update(status="refused-preflight", stopReason=reason)
            return evidence
        atomic_json(path, evidence)
        command = [sys.executable, str(Path(__file__).resolve()), "--child-address-space",
                   str(args.address_space_bytes), "--parent-pid", str(os.getpid()), "--", *allocation_args]
        with (evidence_dir / "stdout.log").open("xb") as stdout, \
                (evidence_dir / "stderr.log").open("xb") as stderr:
            child = subprocess.Popen(command, stdout=stdout, stderr=stderr,
                                     env={**os.environ, "PYTHONUNBUFFERED": "1", "TMPDIR": str(temporary_dir),
                                          "TMP": str(temporary_dir), "TEMP": str(temporary_dir),
                                          "SQLITE_TMPDIR": str(temporary_dir)})
            started = time.monotonic()
            evidence.update(status="running", childPid=child.pid)
            atomic_json(path, evidence)
            while child.poll() is None:
                try:
                    sample = resource_sample(child.pid, evidence_dir)
                except (OSError, ValueError) as error:
                    # /proc may disappear immediately before poll observes exit.
                    try:
                        child.wait(timeout=0.05)
                    except subprocess.TimeoutExpired:
                        raise error
                    break
                _record_sample(evidence, sample)
                evidence["elapsedSeconds"] = max(0, time.monotonic() - started)
                reason = violation(sample, args)
                if reason is None and evidence["elapsedSeconds"] >= args.max_wall_seconds:
                    reason = "wall-time-limit"
                if reason:
                    evidence.update(status="resource-stop", stopReason=reason)
                    atomic_json(path, evidence)
                    terminate_owned(child)
                    break
                atomic_json(path, evidence)
                time.sleep(args.poll_seconds)
            evidence["exitCode"] = child.wait()
            if evidence["status"] == "running":
                evidence.update(status="child-exited-zero" if child.returncode == 0 else "child-failed",
                                stopReason="child-exited" if child.returncode == 0 else "child-nonzero-exit")
    except BaseException as error:
        interrupted = isinstance(error, (KeyboardInterrupt, GuardTerminated))
        evidence.update(status="interrupted" if interrupted else "guard-failed",
                        stopReason="supervisor-interrupted" if interrupted else "guard-error",
                        failure={"type": type(error).__name__})
        raise
    finally:
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        try:
            try:
                if child is not None:
                    terminate_owned(child)
                    evidence["exitCode"] = child.returncode
            finally:
                evidence["elapsedSeconds"] = max(0, time.monotonic() - started)
                evidence["partialState"] = {"scratchDirectoryExists": routes["scratch"].is_dir(),
                                             "outputDirectoryExists": routes["out"].is_dir()}
                atomic_json(path, evidence)
        finally:
            signal.signal(signal.SIGTERM, prior_sigterm)
    return evidence


def parser():
    result = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    result.add_argument("--evidence-dir", required=True)
    result.add_argument("--max-rss-bytes", type=int, default=4 * GIB)
    result.add_argument("--address-space-bytes", type=int, default=5 * GIB)
    result.add_argument("--min-available-bytes", type=int, default=2 * GIB)
    result.add_argument("--min-disk-free-bytes", type=int, default=3 * GIB)
    result.add_argument("--poll-seconds", type=float, default=1.0)
    result.add_argument("--max-wall-seconds", type=float, default=5400)
    result.add_argument("allocation_args", nargs=argparse.REMAINDER)
    return result


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv[:1] == ["--child-address-space"]:
        if len(argv) < 5 or argv[2] != "--parent-pid" or argv[4] != "--":
            raise ValueError("invalid child-wrapper arguments")
        ceiling, parent = int(argv[1]), int(argv[3])
        if ceiling <= 0 or parent <= 0:
            raise ValueError("invalid child-wrapper limits or parent")
        child_routes(argv[5:])
        import resource
        parent_death_guard(parent)
        resource.setrlimit(resource.RLIMIT_AS, (ceiling, ceiling))
        from allocation_diagnostic import main as allocation_main
        return allocation_main(argv[5:])
    result = supervise(parser().parse_args(argv))
    print(json.dumps({key: result[key] for key in ("status", "exitCode", "stopReason")}))
    return 0 if result["status"] == "child-exited-zero" else 1


if __name__ == "__main__":
    raise SystemExit(main())
