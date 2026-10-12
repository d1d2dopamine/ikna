#!/usr/bin/env python3
"""Offline stage-window diagnostics; never allocation or publication evidence.

Use ``with StageRecorder() as recorder`` and ``recorder.measure(name, **metadata)``.
Only the main thread may enter stages. Nested windows are inclusive; exclusive
clocks subtract child windows, but RSS cannot be subtracted or allocated to a
stage. All observed RSS is current residency of this entire shared process.
"""
from __future__ import annotations

from contextlib import contextmanager
from copy import deepcopy
import math
import os
import sys
import threading
import time


def current_rss_bytes():
    """Linux /proc residency, not resource.ru_maxrss (a lifetime high-water mark)."""
    if not sys.platform.startswith("linux"):
        return None
    try:
        with open("/proc/self/statm", encoding="ascii") as source:
            return int(source.read().split()[1]) * os.sysconf("SC_PAGE_SIZE")
    except (OSError, ValueError, IndexError, AttributeError):
        return None


def process_peak_rss_bytes():
    """OS process-lifetime high-water mark; deliberately separate from stages."""
    if sys.platform not in ("linux", "darwin"):
        return None
    try:
        import resource
        value = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        return int(value if sys.platform == "darwin" else value * 1024)
    except (ImportError, OSError, AttributeError, ValueError):
        return None


class StageRecorder:
    """One-shot recorder with bounded per-stage metadata and a joined sampler.

    Reader/clock injection is for deterministic tests. A reader returns bytes or
    None; failures are reported as unavailable samples, never zero-byte memory.
    The sampler interval is a request, not a guaranteed resolution: scheduling
    and Python's GIL can delay samples. Boundary samples cover very short calls.
    """

    def __init__(self, *, sample_interval_seconds=0.01, rss_reader=None,
                 peak_reader=None, wall_clock=None, cpu_clock=None, max_records=1000):
        if not math.isfinite(sample_interval_seconds) or sample_interval_seconds <= 0:
            raise ValueError("sample interval must be finite and positive")
        if not isinstance(max_records, int) or isinstance(max_records, bool) or max_records < 0:
            raise ValueError("max records must be a nonnegative integer")
        self._max_records = max_records
        self._records = []
        self._records_truncated = False
        self.interval = sample_interval_seconds
        self._rss = rss_reader or current_rss_bytes
        self._peak = peak_reader or process_peak_rss_bytes
        self._wall = wall_clock or time.perf_counter
        self._cpu = cpu_clock or time.process_time
        self._rss_source = ("injected-reader" if rss_reader is not None else
                            "linux-proc-self-statm" if sys.platform.startswith("linux") else
                            "unsupported-platform-current-rss")
        self._lock = threading.RLock()
        self._stop = threading.Event()
        self._thread = None
        self._state = "new"
        self._failed = False
        self._stack = []
        self._stages = {}
        self._peak_value = None
        self._rss_reads = 0
        self._rss_unavailable = 0

    @staticmethod
    def _require_main():
        if threading.current_thread() is not threading.main_thread():
            raise RuntimeError("stage recording is restricted to the main thread")

    def __enter__(self):
        self._require_main()
        if self._state != "new":
            raise RuntimeError("recorder is one-shot and cannot be re-entered")
        self._state = "active"
        self._thread = threading.Thread(target=self._sample_loop,
                                        name="selection-rss-sampler", daemon=True)
        try:
            self._thread.start()
        except BaseException:
            self._state = "closed"
            self._failed = True
            self._stop.set()
            raise
        return self

    def __exit__(self, exc_type, exc, traceback):
        self._failed |= exc_type is not None
        self.close()
        return False

    def close(self):
        """Idempotently join the sampler; do not close an unfinished stage."""
        self._require_main()
        if self._stack:
            raise RuntimeError("cannot close recorder while a measured stage is active")
        if self._state == "closed":
            return
        self._stop.set()
        if self._thread is not None and self._thread.ident is not None:
            self._thread.join()
        try:
            self._peak_value = self._read_bytes(self._peak)
        finally:
            self._state = "closed"

    @staticmethod
    def _read_bytes(reader):
        try:
            value = reader()
            return value if isinstance(value, int) and not isinstance(value, bool) and value >= 0 else None
        except Exception:
            return None

    def _sample_locked(self):
        # The lock binds a read to exactly the windows active during that read.
        if not self._stack:
            return
        value = self._read_bytes(self._rss)
        self._rss_reads += 1
        self._rss_unavailable += value is None
        for frame in self._stack:
            frame["rssAttempts"] += 1
            if value is not None:
                frame["rssSamples"] += 1
                previous = frame["sampledPeakRssBytes"]
                frame["sampledPeakRssBytes"] = value if previous is None else max(previous, value)

    def _sample_loop(self):
        while not self._stop.wait(self.interval):
            with self._lock:
                self._sample_locked()

    @contextmanager
    def measure(self, name, **metadata):
        self._require_main()
        if self._state != "active":
            raise RuntimeError("measure requires an active recorder context")
        if not isinstance(name, str) or not name.strip():
            raise ValueError("stage name must be nonempty text")
        with self._lock:
            frame = {"name": name, "metadata": deepcopy(metadata),
                     "wall": self._wall(), "cpu": self._cpu(),
                     "childWall": 0.0, "childCpu": 0.0,
                     "rssAttempts": 0, "rssSamples": 0, "sampledPeakRssBytes": None}
            self._stack.append(frame)
            self._sample_locked()
        failed = False
        try:
            yield
        except BaseException:
            failed = True
            self._failed = True
            raise
        finally:
            with self._lock:
                try:
                    self._sample_locked()
                    elapsed = self._wall() - frame["wall"]
                    cpu = self._cpu() - frame["cpu"]
                except BaseException:
                    self._failed = True
                    raise
                finally:
                    self._stack.pop()
                if self._stack:
                    self._stack[-1]["childWall"] += elapsed
                    self._stack[-1]["childCpu"] += cpu
                stage = self._stages.setdefault(name, {
                    "calls": 0, "failedCalls": 0, "elapsedSeconds": 0.0,
                    "processCpuSeconds": 0.0, "exclusiveElapsedSeconds": 0.0,
                    "exclusiveProcessCpuSeconds": 0.0, "rssAttempts": 0,
                    "rssSamples": 0, "sampledPeakRssBytes": None,
                    "metadataSamples": [], "metadataSamplesTruncated": False})
                stage["calls"] += 1
                stage["failedCalls"] += failed
                stage["elapsedSeconds"] += elapsed
                stage["processCpuSeconds"] += cpu
                stage["exclusiveElapsedSeconds"] += max(0.0, elapsed - frame["childWall"])
                stage["exclusiveProcessCpuSeconds"] += max(0.0, cpu - frame["childCpu"])
                for key in ("rssAttempts", "rssSamples"):
                    stage[key] += frame[key]
                peak = frame["sampledPeakRssBytes"]
                if peak is not None:
                    old = stage["sampledPeakRssBytes"]
                    stage["sampledPeakRssBytes"] = peak if old is None else max(old, peak)
                record = {"stage": name, "metadata": frame["metadata"],
                          "failed": failed, "elapsedSeconds": elapsed,
                          "processCpuSeconds": cpu,
                          "exclusiveElapsedSeconds": max(0.0, elapsed - frame["childWall"]),
                          "exclusiveProcessCpuSeconds": max(0.0, cpu - frame["childCpu"]),
                          "rssAttempts": frame["rssAttempts"], "rssSamples": frame["rssSamples"],
                          "sampledPeakRssBytes": peak}
                if len(self._records) < self._max_records:
                    self._records.append(record)
                else:
                    self._records_truncated = True
                if frame["metadata"] and frame["metadata"] not in stage["metadataSamples"]:
                    if len(stage["metadataSamples"]) < 16:
                        stage["metadataSamples"].append(frame["metadata"])
                    else:
                        stage["metadataSamplesTruncated"] = True

    def snapshot(self):
        """Detached completed-call aggregates for crash diagnostics, never success.

        Active calls are named but not counted until they exit. RSS of an active
        call is not exposed as a completed-stage peak. This does not create a
        resumable selection checkpoint and deliberately omits raw records.
        """
        with self._lock:
            return {"schemaVersion": 1, "status": "incomplete",
                    "publicationSafe": False,
                    "measurementCalls": sum(stage["calls"] for stage in self._stages.values()),
                    "activeStages": [frame["name"] for frame in self._stack],
                    "stages": deepcopy(self._stages),
                    "sampleIntervalSeconds": self.interval,
                    "rssSource": self._rss_source,
                    "rssSemantics": "completed-call sampled whole-process RSS; lower bound, not allocations",
                    "resumable": False}

    def report(self):
        """Return a detached report only after all stages and sampling have stopped."""
        if self._state != "closed":
            raise RuntimeError("close recorder before obtaining its report")
        calls = sum(stage["calls"] for stage in self._stages.values())
        return {"schemaVersion": 1,
                "status": "failed" if self._failed else "complete" if calls else "no-measurements",
                "publicationSafe": False, "measurementCalls": calls,
                "sampleIntervalSeconds": self.interval,
                "rssSource": self._rss_source,
                "rssAvailability": "available" if self._rss_reads > self._rss_unavailable else "unavailable",
                "rssReadAttempts": self._rss_reads, "rssUnavailableReads": self._rss_unavailable,
                "processPeakRssBytes": self._peak_value,
                "processPeakRssSemantics": "process-lifetime high-water mark, not stage-local",
                "timingSemantics": "inclusive nested windows; exclusive clocks subtract measured children; process CPU includes sampler and other threads; instrumentation overhead included",
                "rssSemantics": "sampled stage-window maximum is a lower bound; shared/current whole-process RSS, not exact allocations or isolated stage memory; nested windows overlap; GIL/scheduling may delay samples",
                "records": deepcopy(self._records), "recordsTruncated": self._records_truncated,
                "maxRecords": self._max_records, "stages": deepcopy(self._stages)}
