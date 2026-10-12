#!/usr/bin/env python3
"""Standalone, fail-closed diagnostic of one exact completed-staging prefix.

This never runs a selector workflow, repairs staging, changes repository files,
or admits/publishes content. See README.md before invoking on real evidence.
"""
from __future__ import annotations

import argparse
import cProfile
import hashlib
import importlib
from importlib import metadata
import io
import json
import marshal
import os
from pathlib import Path
import platform
import pstats
import resource
import signal
import sqlite3
import stat
import sys
import tempfile
import time
import zlib

PREFIX_ROWS = 32768
PAIR = ("everyday", "en", "de")
EVIDENCE_LIMIT = 12
ADDRESS_SPACE_BYTES = 2 * 1024**3
CPU_SECONDS = 300
HISTORICAL_LOGICAL = "7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4"
PAIR_SQL = (
    "SELECT c.id,c.context,c.meaning,c.origins_json,c.max_alignment,a.token_count,a.choices_json "
    "FROM candidate c JOIN context_analysis a "
    "ON a.collection=c.collection AND a.lang=c.lang AND a.context_key=c.context_key "
    "WHERE c.collection=? AND c.lang=? AND c.meaning_lang=? ORDER BY c.id"
)
IDS_SQL = "SELECT id FROM candidate WHERE collection=? AND lang=? AND meaning_lang=? ORDER BY id LIMIT ?"
JSON_ENCODER = json.JSONEncoder(ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def file_identity(path):
    value = path.stat()
    require(stat.S_ISREG(value.st_mode), f"not a regular file: {path}")
    return {"device": value.st_dev, "inode": value.st_ino, "sizeBytes": value.st_size,
            "mtimeNs": value.st_mtime_ns, "ctimeNs": value.st_ctime_ns}


def hash_file(path):
    """Bounded-memory bytes hash, rejecting an observed concurrent modification."""
    path = Path(path)
    before = file_identity(path)
    with path.open("rb") as handle:
        opened = os.fstat(handle.fileno())
        require((opened.st_dev, opened.st_ino) == (before["device"], before["inode"]), "file replaced before hashing")
        digest = hashlib.file_digest(handle, "sha256").hexdigest()
    require(file_identity(path) == before, f"file changed while hashing: {path}")
    return digest


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def snapshot_files(paths):
    snapshot = {}
    for path in paths:
        identity = file_identity(path)
        digest = hash_file(path)
        require(file_identity(path) == identity, "file changed during snapshot")
        snapshot[str(path)] = {"identity": identity, "sha256": digest}
    return snapshot


def verify_snapshot(snapshot):
    for path, before in snapshot.items():
        require(file_identity(Path(path)) == before["identity"] and hash_file(Path(path)) == before["sha256"],
                f"bound evidence changed: {path}")


def write_json_new(path, value):
    """Never replace an existing evidence file; encode without a full JSON copy."""
    digest = hashlib.sha256()
    size = 0
    with Path(path).open("xb") as handle:
        for chunk in JSON_ENCODER.iterencode(value):
            raw = chunk.encode("utf-8")
            handle.write(raw)
            digest.update(raw)
            size += len(raw)
        handle.write(b"\n")
        digest.update(b"\n")
        size += 1
        handle.flush()
        os.fsync(handle.fileno())
    return {"sha256": digest.hexdigest(), "sizeBytes": size}


def canonical_hash(value):
    digest = hashlib.sha256()
    for chunk in JSON_ENCODER.iterencode(value):
        digest.update(chunk.encode("utf-8"))
    return digest.hexdigest()


def add_framed(digest, value):
    # A row is small; original JSON feature strings remain strings, not reparsed.
    encoded = JSON_ENCODER.encode(value).encode("utf-8")
    digest.update(len(encoded).to_bytes(8, "big"))
    digest.update(encoded)


def check_digest(value, name):
    require(isinstance(value, str) and len(value) == 64 and
            all(c in "0123456789abcdef" for c in value), f"invalid or missing {name}")


def producer_paths(control):
    work = control / "full-control" / "run-01"
    return {"comparison": control / "CONTROL-COMPARISON.json",
            "guard": control / "guard" / "RESOURCE.json",
            "profile": control / "full-control" / "PROFILE.json",
            "selectionReport": work / "selection.json", "staging": work / "selection.sqlite3"}


def load_producer(control):
    paths = producer_paths(control)
    comparison = read_json(paths["comparison"])
    require(comparison.get("status") == "complete-independent-control-comparison", "independent comparison incomplete")
    for key in ("historicalLogicalIdentityMatches", "reportedIntegrityMatches", "fullSelectedGzipIntegrityRead"):
        require(comparison.get(key) is True, f"comparison gate failed: {key}")
    for key in ("profileFileSha256", "selectionReportFileSha256", "stagingSha256"):
        check_digest(comparison.get(key), key)
    require(type(comparison.get("stagingSizeBytes")) is int and comparison["stagingSizeBytes"] > 0,
            "missing positive stagingSizeBytes")
    guard = read_json(paths["guard"])
    require(guard.get("status") == "child-exited-zero" and guard.get("exitCode") == 0, "producer guard has not succeeded")
    require(hash_file(paths["profile"]) == comparison["profileFileSha256"], "producer profile binding mismatch")
    require(hash_file(paths["selectionReport"]) == comparison["selectionReportFileSha256"], "selection report binding mismatch")
    profile, report = read_json(paths["profile"]), read_json(paths["selectionReport"])
    require(profile.get("status") == "complete-full-saved-input-diagnostic" and
            profile.get("inputScope") == "full-saved-input" and profile.get("publicationSafe") is False,
            "producer is not a completed non-publishing full control")
    settings = profile.get("settings", {})
    require(settings.get("runs") == 1 and settings.get("max_deck") == 8000 and
            settings.get("quality_policy") == "boundary-diversity-v2" and settings.get("measurement_mode") == "stages",
            "producer settings differ from the authorized control")
    require(isinstance(profile.get("runs"), list) and len(profile["runs"]) == 1 and profile["runs"][0].get("run") == 1,
            "producer must contain exactly one completed run")
    require(report.get("morphologyUsed") is False, "producer morphology must be disabled")
    require(report.get("publicationSafe") is False and report.get("status") == "admitted-everyday-selection-review",
            "selection report is not admitted non-publishing evidence")
    require(report.get("policy", {}).get("maxTargetsPerLevel") == 8000 and
            report.get("qualityPolicy", {}).get("id") == "boundary-diversity-v2", "selection report policy mismatch")
    run = profile["runs"][0]
    require(run.get("stagingBytes") == comparison["stagingSizeBytes"], "producer staging size binding mismatch")
    require(report.get("selectedMaterial", {}).get("logicalSha256") == HISTORICAL_LOGICAL and
            run.get("outputIdentity", {}).get("selectedLogicalSha256") == HISTORICAL_LOGICAL and
            comparison.get("independentlyComputedSelected", {}).get("logicalSha256") == HISTORICAL_LOGICAL,
            "historical selected logical identity mismatch")
    require(canonical_hash(report) == run.get("outputIdentity", {}).get("reportSha256"), "producer canonical report mismatch")
    require(run.get("stageCounts") == report.get("stage"), "producer stage counts mismatch")
    require(run.get("stageMetrics", {}).get("status") == "complete", "producer stages incomplete")
    require(report.get("stage", {}).get("uniqueCandidates") == 11099490 and
            report.get("stage", {}).get("duplicateCandidates") == 0, "producer is not the full unique control pool")
    require(profile.get("inputPins", {}).get("pool") == report.get("admittedInput", {}).get("poolSha256") and
            profile.get("inputPins", {}).get("poolReport") == report.get("admittedInput", {}).get("poolReportSha256") and
            profile.get("inputPins", {}).get("registry") == report.get("admittedInput", {}).get("registrySha256"),
            "producer admitted-input binding mismatch")
    return paths, comparison, profile, report


def verify_source_pins(repo, profile, report):
    tools_dir = (repo / "tools" / "catalog").resolve(strict=True)
    pins = profile.get("pipelineSha256")
    require(isinstance(pins, dict) and bool(pins), "producer source pins absent")
    expected_paths = {str(p.relative_to(tools_dir)) for p in tools_dir.rglob("*.py")}
    expected_paths.update(("sources/selection-quarantine.json", "requirements-selection-quality.txt"))
    require(set(pins) == expected_paths, "producer/current source path sets differ")
    resolved = {}
    for name, expected in sorted(pins.items()):
        check_digest(expected, name)
        relative = Path(name)
        require(not relative.is_absolute() and ".." not in relative.parts, "unsafe producer source path")
        path = (tools_dir / relative).resolve(strict=True)
        require(path.is_relative_to(tools_dir), "source pin escapes catalogue tools")
        require(hash_file(path) == expected, f"producer source changed: {name}")
        resolved[name] = {"path": str(path), "sha256": expected}
    for name, expected in report.get("selectionPipelineSha256", {}).items():
        require(pins.get(name) == expected, f"report/profile source pins disagree: {name}")
    registry = tools_dir / "sources" / "catalogue-v2-sources.json"
    require(hash_file(registry) == profile["inputPins"]["registry"], "source registry changed")
    require(report.get("qualityPolicy", {}).get("sourceQuarantine", {}).get("sha256") ==
            pins["sources/selection-quarantine.json"], "quarantine identity mismatch")
    return resolved


def verify_runtime(profile, report, selection):
    environment = profile.get("environment", {})
    current = {"python": platform.python_version(), "platform": platform.platform(),
               "machine": platform.machine(), "sqlite": sqlite3.sqlite_version}
    for key, value in current.items():
        require(environment.get(key) == value, f"runtime differs from producer: {key}")
    require(report.get("selectionEnvironment", {}).get("python") == current["python"] and
            report.get("selectionEnvironment", {}).get("zlib") == zlib.ZLIB_RUNTIME_VERSION, "Python/zlib report mismatch")
    current["zlib"] = zlib.ZLIB_RUNTIME_VERSION
    pin = environment.get("icuRuntimePin", {})
    icu_path = Path(os.environ.get("IKNA_ICU_LIBRARY", ""))
    require(icu_path.is_absolute() and icu_path.is_file(), "explicit producer ICU library pin is required")
    require(icu_path.name == pin.get("fileName") and hash_file(icu_path) == pin.get("sha256") and
            os.environ.get("IKNA_ICU_VERSION") == pin.get("requiredVersion"), "ICU runtime pin mismatch")
    segmentation = selection.prepare(report["admittedInput"]["learn"])
    require(segmentation == report["selectionEnvironment"]["segmentation"], "segmentation runtime differs from producer")
    maps = Path("/proc/self/maps")
    require(maps.is_file(), "mapped-library verification unavailable")
    paths = set()
    for line in maps.read_text(encoding="utf-8").splitlines():
        parts = line.split(maxsplit=5)
        if len(parts) == 6 and Path(parts[5]).name.startswith("libicu"):
            path = Path(parts[5])
            require(path.is_file(), "mapped ICU file unavailable")
            paths.add(path.resolve())
    mapped = {"available": bool(paths), "files": [
        {"fileName": p.name, "sha256": hash_file(p), "sizeBytes": p.stat().st_size} for p in sorted(paths)]}
    require(mapped["available"] and mapped == environment.get("mappedIcuLibraries"), "mapped ICU bytes differ from producer")
    # The English probe uses no Japanese analyzer, but verify inherited feature
    # dependencies without allocating a fresh analyzer or recomputing any choices.
    japanese = report.get("qualityPolicy", {}).get("japaneseBoundary", {})
    require(metadata.version("SudachiPy") == japanese.get("version") and
            metadata.version("SudachiDict-core") == japanese.get("dictionaryVersion"), "Japanese dependency versions changed")
    dictionary_module = importlib.import_module("sudachidict_core")
    dictionary = Path(dictionary_module.__file__).parent / "resources" / "system.dic"
    require(hash_file(dictionary) == japanese.get("dictionarySha256"), "inherited Japanese dictionary changed")
    current.update(segmentation=segmentation, mappedIcuLibraries=mapped,
                   icuLibraryPath=str(icu_path.resolve()), inheritedJapaneseFeatures=japanese)
    return current


def verify_staging(path, comparison, previous_identity=None):
    for suffix in ("-wal", "-shm", "-journal"):
        require(not Path(str(path) + suffix).exists(), "staging has a live/recovery sidecar")
    identity = file_identity(path)
    require(identity["sizeBytes"] == comparison["stagingSizeBytes"], "staging byte size mismatch")
    if previous_identity is not None:
        require(identity == previous_identity, "staging file identity changed")
    require(hash_file(path) == comparison["stagingSha256"], "staging byte hash mismatch")
    require(file_identity(path) == identity, "staging changed during verification")
    return identity


def open_readonly(path):
    db = sqlite3.connect(path.as_uri() + "?mode=ro", uri=True, timeout=0)
    try:
        db.execute("PRAGMA query_only=ON")
        require(db.execute("PRAGMA query_only").fetchone() == (1,), "SQLite query_only not enabled")
        db.execute("BEGIN")
        return db
    except BaseException:
        db.close()
        raise


def verify_schema(db):
    expected = {
        "candidate": ["id", "collection", "lang", "meaning_lang", "context_key", "context", "meaning", "origins_json", "max_alignment"],
        "context_analysis": ["collection", "lang", "context_key", "token_count", "choices_json"],
    }
    for table, columns in expected.items():
        actual = [row[1] for row in db.execute(f"PRAGMA table_info({table})")]
        require(actual == columns, f"staging schema differs: {table}")
    require([row[2] for row in db.execute("PRAGMA index_info(candidate_pair)")] ==
            ["collection", "lang", "meaning_lang", "id"], "candidate_pair index differs")


def connection_settings(db):
    return {name: db.execute("PRAGMA " + name).fetchone()[0] for name in
            ("query_only", "cache_size", "mmap_size", "temp_store", "journal_mode", "locking_mode", "page_size")}


def prefix_identity(db, limit=PREFIX_ROWS):
    ids = [row[0] for row in db.execute(IDS_SQL, (*PAIR, limit))]
    require(len(ids) == limit and len(set(ids)) == limit, "prefix is short or candidate IDs duplicate")
    row_digest, id_digest = hashlib.sha256(), hashlib.sha256()
    count = 0
    for row in db.execute(PAIR_SQL + " LIMIT ?", (*PAIR, limit)):
        require(count < limit and len(row) == 7 and row[0] == ids[count], "analysis join lost/reordered/duplicated a candidate")
        require(type(row[5]) is int and isinstance(row[6], str), "analysis row is incomplete")
        add_framed(row_digest, row)
        add_framed(id_digest, row[0])
        count += 1
    require(count == limit, "analysis join has fewer rows than candidate prefix")
    return {"candidateRows": count, "orderedCandidateIdsSha256": id_digest.hexdigest(),
            "sevenValueRowsSha256": row_digest.hexdigest(), "firstCandidateId": ids[0], "lastCandidateId": ids[-1],
            "encoding": "UTF-8 canonical JSON per row; 8-byte big-endian length prefix; exact stored JSON strings"}


class PrefixConnection:
    """The only measured-path addition is one parameterized SQL LIMIT."""
    def __init__(self, db, limit=PREFIX_ROWS):
        self.db, self.limit, self.calls = db, limit, 0

    def execute(self, sql, parameters):
        require(sql == PAIR_SQL and tuple(parameters) == PAIR, "unexpected pair-evidence SQL or parameters")
        require(self.calls == 0, "pair evidence executed more than one query")
        self.calls += 1
        return self.db.execute(sql + " LIMIT ?", (*parameters, self.limit))


def process_evidence():
    usage = resource.getrusage(resource.RUSAGE_SELF)
    return {"processLifetimePeakRssBytes": int(usage.ru_maxrss * 1024),
            "processUserSeconds": usage.ru_utime, "processSystemSeconds": usage.ru_stime}


def install_limits():
    require(sys.platform.startswith("linux"), "probe requires Linux resource limits and /proc")
    applied = {}
    for name, kind, soft, hard in (("addressSpaceBytes", resource.RLIMIT_AS, ADDRESS_SPACE_BYTES, ADDRESS_SPACE_BYTES),
                                    ("cpuSeconds", resource.RLIMIT_CPU, CPU_SECONDS - 1, CPU_SECONDS)):
        old_soft, old_hard = resource.getrlimit(kind)
        if old_hard != resource.RLIM_INFINITY:
            hard = min(hard, old_hard)
        if old_soft != resource.RLIM_INFINITY:
            soft = min(soft, old_soft)
        soft = min(soft, hard)
        require(soft > 0, f"unusable existing limit: {name}")
        resource.setrlimit(kind, (soft, hard))
        applied[name] = {"soft": soft, "hard": hard}
    return applied


def resource_preflight(output):
    import shutil
    available = None
    for line in Path("/proc/meminfo").read_text().splitlines():
        if line.startswith("MemAvailable:"):
            available = int(line.split()[1]) * 1024
    free = shutil.disk_usage(output).free
    require(available is not None and available >= 2 * 1024**3, "less than 2 GiB host memory available")
    require(free >= 4 * 1024**3, "less than 4 GiB disk available")
    return {"hostAvailableBytes": available, "diskFreeBytes": free,
            "scope": "preflight snapshot only; hidden cgroup limits unknown; no sampled RSS/headroom guarantee"}


def profile_summary(profiler):
    stats = pstats.Stats(profiler)
    entries = []
    for (path, line, function), (primitive, calls, own, cumulative, callers) in stats.stats.items():
        entries.append({"path": path, "line": line, "function": function, "primitiveCalls": primitive,
                        "calls": calls, "selfSeconds": own, "cumulativeSeconds": cumulative})
    wanted = {"pair_evidence", "choose_contexts", "context_overlap", "_written_counts", "word_spans",
              "near_duplicate", "normalized_words", "context_quality_key", "stable_hash", "_origin_families"}
    focused = [entry for entry in entries if entry["function"] in wanted]
    stream = io.StringIO()
    view = pstats.Stats(profiler, stream=stream)
    view.sort_stats("cumulative").print_stats(40)
    view.sort_stats("tottime").print_stats(40)
    view.print_callers("choose_contexts|context_overlap|near_duplicate|_written_counts")
    view.print_callees("choose_contexts|context_overlap|near_duplicate|_written_counts")
    return {"topCumulative": sorted(entries, key=lambda x: x["cumulativeSeconds"], reverse=True)[:40],
            "topSelf": sorted(entries, key=lambda x: x["selfSeconds"], reverse=True)[:40],
            "focusedCalls": focused}, stream.getvalue()


def validate_baseline(path, expected):
    reference = read_json(path)
    require(reference.get("status") == "complete-dense-pair-prefix-diagnostic" and reference.get("mode") == "baseline",
            "reference is not a completed baseline")
    for key in ("producerBindings", "helperSha256", "settings", "runtime", "prefixIdentity", "sqliteConnection"):
        require(reference.get(key) == expected[key], f"baseline contract differs: {key}")
    output = path.parent / "EVIDENCE.json"
    require(hash_file(output) == reference.get("outputIdentity", {}).get("sha256") and
            output.stat().st_size == reference["outputIdentity"]["sizeBytes"], "baseline evidence changed")
    return reference


def run(args, limits):
    total_start, total_cpu = time.perf_counter(), time.process_time()
    repo, control, output = args.repo.resolve(strict=True), args.control_dir.resolve(strict=True), args.output_dir.resolve()
    require(not output.exists(), "output directory must be new")
    require(not output.is_relative_to(repo) and not repo.is_relative_to(output), "output must be outside repository")
    require(not output.is_relative_to(control) and not control.is_relative_to(output), "output must be disjoint from producer control")
    require(output.parent.is_dir(), "output parent must already exist")
    require((args.mode == "cprofile") == bool(args.reference_baseline), "cprofile requires --reference-baseline; baseline forbids it")
    paths = producer_paths(control)
    producer_snapshot = snapshot_files(path for key, path in paths.items() if key != "staging")
    paths, comparison, profile, report = load_producer(control)
    verify_snapshot(producer_snapshot)
    source_pins = verify_source_pins(repo, profile, report)
    output.mkdir()
    evidence = {"status": "incomplete", "publicationSafe": False, "resumable": False,
                "mode": args.mode, "pid": os.getpid(), "limits": limits,
                "paths": {key: str(path) for key, path in paths.items()},
                "settings": {"pair": list(PAIR), "prefixRows": PREFIX_ROWS, "evidenceContextLimit": EVIDENCE_LIMIT,
                             "diverse": True, "morphology": None, "producerMaxDeck": 8000},
                "helperSha256": {Path(__file__).name: hash_file(Path(__file__))},
                "producerBindings": {key: comparison[key] for key in
                    ("profileFileSha256", "selectionReportFileSha256", "stagingSha256", "stagingSizeBytes")},
                "sourcePinsWithResolvedPaths": source_pins,
                "limitations": ["exact early pair prefix; not a full pair, final allocation, selected deck or speedup",
                    "fresh-process caches differ from the full producer's inherited process state",
                    "OS caches uncontrolled and warmed by mandatory preflight hashing/row checks",
                    "cProfile cumulative times overlap and include instrumentation overhead",
                    "SQLite cursor-fetch work may appear in enclosing function self time",
                    "pair timing excludes source/staging hashes, prefix checks, imports and artifact serialization",
                    "resource peak is whole-process lifetime RSS, not pair-exclusive allocation",
                    "hard address-space and CPU limits do not guarantee host or cgroup headroom",
                    "full staging bytes hashed before and after; no production staging-reuse or resume implementation"]}
    evidence["producerBindings"].update(
        comparisonFileSha256=producer_snapshot[str(paths["comparison"])]["sha256"],
        guardResourceFileSha256=producer_snapshot[str(paths["guard"])]["sha256"])
    write_json_new(output / "STARTED.json", evidence)
    db = None
    try:
        evidence["resourcePreflight"] = resource_preflight(output)
        tmp = output / "tmp"
        tmp.mkdir()
        for name in ("TMPDIR", "TMP", "TEMP", "SQLITE_TMPDIR"):
            os.environ[name] = str(tmp)
        tempfile.tempdir = None
        evidence["temporaryDirectory"] = str(tmp)
        staging_identity = verify_staging(paths["staging"], comparison)
        evidence["stagingFileIdentity"] = staging_identity
        tools_dir = repo / "tools" / "catalog"
        sys.dont_write_bytecode = True
        sys.path.insert(0, str(tools_dir))
        selection = importlib.import_module("selection_experiment")
        policy = importlib.import_module("selection_policy")
        require(Path(selection.__file__).resolve() == (tools_dir / "selection_experiment.py").resolve(), "wrong selector module imported")
        require(selection.pair_evidence.__module__ == "selection_experiment" and
                selection.pair_evidence.__code__.co_filename == str(tools_dir / "selection_experiment.py"), "pair function is not original imported implementation")
        require(selection.choose_contexts is policy.choose_contexts, "chooser has been replaced")
        defaults = selection.parser().parse_args(["--candidates", "unused", "--json", "unused-j", "--markdown", "unused-m",
                                                "--preview", "unused-p", "--staging", "unused-s"])
        require(defaults.evidence_context_limit == EVIDENCE_LIMIT and defaults.morphology_db is None, "producer-derived function defaults changed")
        evidence["runtime"] = verify_runtime(profile, report, selection)
        db = open_readonly(paths["staging"])
        verify_schema(db)
        evidence["sqliteConnection"] = connection_settings(db)
        evidence["prefixIdentity"] = prefix_identity(db, PREFIX_ROWS)
        evidence["queryPlan"] = list(db.execute("EXPLAIN QUERY PLAN " + PAIR_SQL + " LIMIT ?", (*PAIR, PREFIX_ROWS)))
        baseline_files = (snapshot_files((args.reference_baseline.resolve(), args.reference_baseline.resolve().parent / "EVIDENCE.json"))
                          if args.reference_baseline else {})
        baseline = validate_baseline(args.reference_baseline.resolve(strict=True), evidence) if args.reference_baseline else None
        verify_snapshot(baseline_files)
        cache_before = policy._written_counts.cache_info()._asdict()
        require(cache_before["hits"] == 0 and cache_before["misses"] == 0 and cache_before["currsize"] == 0,
                "written-token cache was not fresh")
        adapter = PrefixConnection(db, PREFIX_ROWS)
        profiler = cProfile.Profile() if args.mode == "cprofile" else None
        cpu_start, start = time.process_time(), time.perf_counter()
        if profiler:
            targets, counters = profiler.runcall(selection.pair_evidence, adapter, *PAIR, None, EVIDENCE_LIMIT, diverse=True)
        else:
            targets, counters = selection.pair_evidence(adapter, *PAIR, None, EVIDENCE_LIMIT, diverse=True)
        elapsed, cpu = time.perf_counter() - start, time.process_time() - cpu_start
        evidence["pairMeasurement"] = {"elapsedSeconds": elapsed, "processCpuSeconds": cpu, **process_evidence()}
        evidence["writtenTokenCache"] = {"before": cache_before, "after": policy._written_counts.cache_info()._asdict()}
        require(adapter.calls == 1 and counters.get("candidateRows") == PREFIX_ROWS, "function did not consume the exact prefix")
        require(prefix_identity(db, PREFIX_ROWS) == evidence["prefixIdentity"], "consumed row stream changed")
        db.close()
        db = None
        evidence["pairStats"] = dict(counters)
        evidence["returnedTargets"] = len(targets)
        evidence["outputIdentity"] = write_json_new(output / "EVIDENCE.json", {"targets": targets, "stats": dict(counters)})
        del targets, counters
        if profiler:
            profiler.create_stats()
            with (output / "PAIR.pstats").open("xb") as handle:
                marshal.dump(profiler.stats, handle)
            evidence["callProfile"], summary = profile_summary(profiler)
            with (output / "PROFILE-TOP.txt").open("x", encoding="utf-8") as handle:
                handle.write(summary)
            evidence["profileArtifacts"] = {name: hash_file(output / name) for name in ("PAIR.pstats", "PROFILE-TOP.txt")}
        if baseline:
            require(evidence["outputIdentity"] == baseline["outputIdentity"], "baseline/profiled complete returned evidence differs")
            verify_snapshot(baseline_files)
            evidence["baselineComparison"] = {"path": str(args.reference_baseline.resolve()),
                "sha256": baseline_files[str(args.reference_baseline.resolve())]["sha256"], "exactReturnedEvidenceEqual": True,
                "baselinePairElapsedSeconds": baseline["pairMeasurement"]["elapsedSeconds"],
                "profileOverheadRatio": elapsed / baseline["pairMeasurement"]["elapsedSeconds"]}
        verify_staging(paths["staging"], comparison, staging_identity)
        require(verify_source_pins(repo, profile, report) == source_pins, "source pins changed during diagnostic")
        require(hash_file(Path(__file__)) == evidence["helperSha256"][Path(__file__).name], "probe helper changed")
        verify_snapshot(producer_snapshot)
        require(verify_runtime(profile, report, selection) == evidence["runtime"], "runtime changed during diagnostic")
        evidence.update(status="complete-dense-pair-prefix-diagnostic", totalElapsedSeconds=time.perf_counter() - total_start,
                        totalProcessCpuSeconds=time.process_time() - total_cpu, finalProcessEvidence=process_evidence())
        write_json_new(output / "PROBE.json", evidence)
        return evidence
    except BaseException as exc:
        evidence.update(status="failed", failure={"type": type(exc).__name__, "message": str(exc)[:2000]},
                        totalElapsedSeconds=time.perf_counter() - total_start, finalProcessEvidence=process_evidence())
        try:
            write_json_new(output / "FAILED.json", evidence)
        except (OSError, MemoryError):
            pass
        raise
    finally:
        if db is not None:
            db.close()


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--repo", required=True, type=Path)
    result.add_argument("--control-dir", required=True, type=Path)
    result.add_argument("--output-dir", required=True, type=Path)
    result.add_argument("--mode", required=True, choices=("baseline", "cprofile"))
    result.add_argument("--reference-baseline", type=Path)
    return result


def limit_signal(signum, frame):
    raise RuntimeError(f"probe interrupted by signal {signum}")


def main(argv=None):
    args = parser().parse_args(argv)
    sys.dont_write_bytecode = True
    limits = install_limits()
    signal.signal(signal.SIGXCPU, limit_signal)
    signal.signal(signal.SIGTERM, limit_signal)
    try:
        result = run(args, limits)
    except (Exception, KeyboardInterrupt) as exc:
        print(f"Probe refused/failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({"status": result["status"], "mode": result["mode"], "outputIdentity": result["outputIdentity"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
