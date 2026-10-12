#!/usr/bin/env python3
"""Linux resource supervisor for one owned, non-publishing selection diagnostic.

Run with guard options, then ``--`` and selection_profile.py arguments. This is
not a general command runner. Partial SQLite and stage evidence cannot resume
selection. Sampled limits can overshoot between polls; RLIMIT_AS is a separate
hard virtual-address-space ceiling, not an RSS limit or host-memory guarantee.
"""
from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

GIB = 1024 ** 3


class GuardTerminated(RuntimeError):
    """SIGTERM routed through owned-child cleanup."""


def parent_death_guard(expected_parent):
    """Linux child dies when its owning supervisor dies, including SIGKILL.

    Checking the expected PID after installing PR_SET_PDEATHSIG closes the race
    where the supervisor dies before the child installs the kernel death signal.
    """
    import ctypes
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(1, signal.SIGTERM, 0, 0, 0) != 0:  # PR_SET_PDEATHSIG
        raise OSError(ctypes.get_errno(), "cannot install parent-death signal")
    if os.getppid() != expected_parent:
        raise RuntimeError("owning supervisor exited before child startup")


def handle_sigterm(signum, frame):
    raise GuardTerminated("supervisor received SIGTERM")


def atomic_json(path, value):
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    temporary.replace(path)


def process_memory(pid):
    fields = {}
    for line in Path(f"/proc/{pid}/status").read_text(encoding="ascii").splitlines():
        if line.startswith(("VmRSS:", "VmSize:")):
            key, value, unit = line.split()
            if unit != "kB":
                raise ValueError("unexpected process memory unit")
            fields[key.rstrip(":")] = int(value) * 1024
    if "VmRSS" not in fields or "VmSize" not in fields:
        raise ValueError("current process RSS/address space unavailable")
    return {"rssBytes": fields["VmRSS"], "virtualBytes": fields["VmSize"]}


def memory_headroom():
    host = None
    for line in Path("/proc/meminfo").read_text(encoding="ascii").splitlines():
        if line.startswith("MemAvailable:"):
            _, value, unit = line.split()
            if unit != "kB":
                raise ValueError("unexpected MemAvailable unit")
            host = int(value) * 1024
    if host is None or host < 0:
        raise ValueError("host available memory unavailable")
    # A visible v2 limit supplements MemAvailable. Hidden/unmounted cgroups
    # are explicitly unknown, never interpreted as a verified unlimited budget.
    result = {"hostAvailableBytes": host, "availableBytes": host,
              "cgroupVisibility": "unavailable", "cgroupHeadroomBytes": None}
    root = Path("/sys/fs/cgroup")
    relative = next((line.split("::", 1)[1] for line in
                     Path("/proc/self/cgroup").read_text(encoding="ascii").splitlines()
                     if line.startswith("0::")), None)
    if relative is None:
        return result
    current = (root / relative.lstrip("/")).resolve()
    if not current.is_relative_to(root.resolve()):
        raise ValueError("cgroup path outside visible mount")
    limits = []
    visible = False
    while True:
        maximum, used = current / "memory.max", current / "memory.current"
        if maximum.exists() or used.exists():
            # If a visible source is incomplete/unreadable, do not silently omit it.
            raw = maximum.read_text(encoding="ascii").strip()
            usage = int(used.read_text(encoding="ascii").strip())
            visible = True
            if raw != "max":
                limits.append(max(0, int(raw) - usage))
        if current == root.resolve():
            break
        current = current.parent
    if visible:
        result["cgroupVisibility"] = "visible-v2"
        if limits:
            result["cgroupHeadroomBytes"] = min(limits)
            result["availableBytes"] = min(host, min(limits))
    return result


def resource_sample(pid, directory):
    return {**process_memory(pid), **memory_headroom(),
            "diskFreeBytes": shutil.disk_usage(directory).free,
            "monotonicSeconds": time.monotonic()}


def violation(sample, args):
    if sample["rssBytes"] >= args.max_rss_bytes:
        return "rss-limit"
    if sample["availableBytes"] < args.min_available_bytes:
        return "available-memory-headroom"
    if sample["diskFreeBytes"] < args.min_disk_free_bytes:
        return "disk-headroom"
    return None


def terminate_owned(child):
    """Only signal the Popen we created, then reap it even after forced kill."""
    if child.poll() is None:
        try:
            child.terminate()
        except ProcessLookupError:
            pass
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            try:
                child.kill()
            except ProcessLookupError:
                pass
            child.wait()
    else:
        child.wait()


def existing_parent(path):
    while not path.exists():
        path = path.parent
    return path


def supervise(args):
    if not sys.platform.startswith("linux"):
        raise ValueError("resource guard requires Linux /proc and RLIMIT_AS")
    import resource
    if not hasattr(resource, "RLIMIT_AS"):
        raise ValueError("RLIMIT_AS is unavailable")
    for value in (args.max_rss_bytes, args.address_space_bytes,
                  args.min_available_bytes, args.min_disk_free_bytes):
        if value <= 0:
            raise ValueError("resource limits must be positive")
    if args.address_space_bytes <= args.max_rss_bytes:
        raise ValueError("address-space ceiling must exceed RSS limit")
    if not math.isfinite(args.poll_seconds) or not 0 < args.poll_seconds <= 10:
        raise ValueError("poll seconds must be finite and in (0, 10]")
    hard = resource.getrlimit(resource.RLIMIT_AS)[1]
    if hard != resource.RLIM_INFINITY and hard < args.address_space_bytes:
        raise ValueError("existing hard address-space ceiling is below requested limit")
    profile_args = args.profile_args
    if profile_args[:1] == ["--"]:
        profile_args = profile_args[1:]
    destination_parser = argparse.ArgumentParser(add_help=False)
    destination_parser.add_argument("--output-dir", required=True)
    destination, _ = destination_parser.parse_known_args(profile_args)
    output = Path(destination.output_dir).resolve()
    evidence_dir = Path(args.evidence_dir).resolve()
    if evidence_dir.exists() or output.exists():
        raise ValueError("evidence and profile output directories must be new")
    if evidence_dir in output.parents or output in evidence_dir.parents or evidence_dir == output:
        raise ValueError("evidence and profile output directories must be disjoint")
    if existing_parent(output).stat().st_dev != existing_parent(evidence_dir).stat().st_dev:
        raise ValueError("profile output and guard evidence must share the monitored filesystem")
    evidence_dir.mkdir(parents=True)
    evidence = {"schemaVersion": 1, "status": "preflight", "publicationSafe": False,
                "resumable": False, "childPid": None, "exitCode": None, "sampleCount": 0,
                "sampledPeakRssBytes": None, "minimumAvailableBytes": None,
                "minimumDiskFreeBytes": None,
                "limits": {key: getattr(args, key) for key in
                           ("max_rss_bytes", "address_space_bytes", "min_available_bytes",
                            "min_disk_free_bytes", "poll_seconds")},
                "profileArgs": profile_args,
                "limitations": ["sampled thresholds can overshoot between polls",
                                "RLIMIT_AS limits virtual address space, not RSS or total host use",
                                "unavailable cgroup visibility leaves hidden memory limits unknown",
                                "partial SQLite/output is not resumable; successful child exit is diagnostic only",
                                "supervisor SIGKILL leaves last monitoring snapshot incomplete; kernel parent-death signal terminates child, adoptive parent must reap it"]}
    path = evidence_dir / "RESOURCE.json"
    temporary_dir = evidence_dir / "tmp"
    temporary_dir.mkdir()
    evidence["temporaryDirectory"] = str(temporary_dir)
    evidence["temporaryDirectoryContract"] = "child TMPDIR/TMP/TEMP and SQLITE_TMPDIR pinned to monitored filesystem"
    child = None
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
                   str(args.address_space_bytes), "--parent-pid", str(os.getpid()), "--", *profile_args]
        with (evidence_dir / "stdout.log").open("xb") as stdout, \
                (evidence_dir / "stderr.log").open("xb") as stderr:
            child = subprocess.Popen(command, stdout=stdout, stderr=stderr,
                                     env={**os.environ, "PYTHONUNBUFFERED": "1", "TMPDIR": str(temporary_dir),
                                          "TMP": str(temporary_dir), "TEMP": str(temporary_dir),
                                          "SQLITE_TMPDIR": str(temporary_dir)})
            evidence.update(status="running", childPid=child.pid)
            atomic_json(path, evidence)
            while child.poll() is None:
                try:
                    sample = resource_sample(child.pid, evidence_dir)
                except (OSError, ValueError) as error:
                    # /proc drops memory fields just before waitpid observes exit.
                    # Briefly settle that race; a still-live unreadable child fails closed.
                    try:
                        child.wait(timeout=0.05)
                    except subprocess.TimeoutExpired:
                        raise error
                    break
                evidence["sampleCount"] += 1
                evidence["latestSample"] = sample
                for key, value, operation in (
                    ("sampledPeakRssBytes", sample["rssBytes"], max),
                    ("minimumAvailableBytes", sample["availableBytes"], min),
                    ("minimumDiskFreeBytes", sample["diskFreeBytes"], min)):
                    old = evidence[key]
                    evidence[key] = value if old is None else operation(old, value)
                reason = violation(sample, args)
                if reason:
                    evidence.update(status="resource-stop", stopReason=reason)
                    atomic_json(path, evidence)
                    terminate_owned(child)
                    break
                atomic_json(path, evidence)
                time.sleep(args.poll_seconds)
            evidence["exitCode"] = child.wait()
            if evidence["status"] == "running":
                evidence["status"] = "child-exited-zero" if child.returncode == 0 else "child-failed"
    except BaseException as error:
        evidence.update(status="interrupted" if isinstance(error, (KeyboardInterrupt, GuardTerminated)) else "guard-failed",
                        failure={"type": type(error).__name__, "message": str(error)[:2000]})
        raise
    finally:
        # A second SIGTERM must not interrupt reaping the already owned child.
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        try:
            if child is not None:
                terminate_owned(child)
                evidence["exitCode"] = child.returncode
            atomic_json(path, evidence)
        finally:
            signal.signal(signal.SIGTERM, prior_sigterm)
    return evidence


def parser():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--evidence-dir", required=True)
    ap.add_argument("--max-rss-bytes", type=int, default=4 * GIB)
    ap.add_argument("--address-space-bytes", type=int, default=5 * GIB)
    ap.add_argument("--min-available-bytes", type=int, default=2 * GIB)
    ap.add_argument("--min-disk-free-bytes", type=int, default=4 * GIB)
    ap.add_argument("--poll-seconds", type=float, default=1.0)
    ap.add_argument("profile_args", nargs=argparse.REMAINDER)
    return ap


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv[:1] == ["--child-address-space"]:
        import resource
        ceiling = int(argv[1])
        if argv[2:3] != ["--parent-pid"] or argv[4:5] != ["--"]:
            raise ValueError("invalid child arguments")
        parent_death_guard(int(argv[3]))
        resource.setrlimit(resource.RLIMIT_AS, (ceiling, ceiling))
        from selection_profile import main as profile_main
        return profile_main(argv[5:])
    result = supervise(parser().parse_args(argv))
    print(json.dumps({"status": result["status"], "exitCode": result["exitCode"]}))
    return 0 if result["status"] == "child-exited-zero" else 1


if __name__ == "__main__":
    raise SystemExit(main())
