#!/usr/bin/env python3
"""Resource-explicit, non-publishing diagnostics for the unchanged admitted selection path."""
from __future__ import annotations

import argparse
import cProfile
from contextlib import contextmanager
import hashlib
import json
import os
import platform
import pstats
import sqlite3
import time
from pathlib import Path

import everyday_selection as admitted
import selection_experiment as selection
from selection_metrics import StageRecorder

HERE = Path(__file__).resolve().parent
# Cumulative times overlap. In particular choose_contexts also runs inside
# pair_evidence; these are call-boundary measurements, not disjoint phases.
BOUNDARIES = {
    "verification": ("everyday_selection.py", "validate_pool"),
    "staging": ("selection_experiment.py", "stage"),
    "ranks": ("selection_experiment.py", "build_ranks"),
    "contextAnalysis": ("selection_experiment.py", "build_context_analysis"),
    "pairEvidence": ("selection_experiment.py", "pair_evidence"),
    "targetOrdering": ("selection_policy.py", "select_target_ids"),
    "contextChoice": ("selection_policy.py", "choose_contexts"),
    "selectedRowOutput": ("selection_output.py", "add"),
    "selectedOutputFinalize": ("selection_output.py", "finish"),
}


def canonical_hash(value) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                     separators=(",", ":")).encode()).hexdigest()


def process_peak_rss():
    """Process lifetime high-water mark, never a stage-local memory measurement."""
    try:
        import resource
        value = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    except (ImportError, AttributeError):
        return None
    return int(value if platform.system() == "Darwin" else value * 1024)


def boundary_stats(profiler):
    stats = pstats.Stats(profiler).stats
    result = {}
    for label, (filename, name) in BOUNDARIES.items():
        matched = [values for (path, _line, function), values in stats.items()
                   if Path(path).name == filename and function == name]
        result[label] = {
            "calls": sum(v[1] for v in matched),
            "selfSeconds": sum(v[2] for v in matched),
            "cumulativeSeconds": sum(v[3] for v in matched),
        }
    return result


def pipeline_identity():
    pins = {str(path.relative_to(HERE)): admitted.sha256(path)
            for path in sorted(HERE.rglob("*.py"))}
    for name in ("sources/selection-quarantine.json", "requirements-selection-quality.txt"):
        pins[name] = admitted.sha256(HERE / name)
    return pins


def loaded_icu_libraries():
    """Pin actual mapped Linux ICU companions/data, not merely a guessed sibling."""
    maps = Path("/proc/self/maps")
    if not maps.exists():
        return {"available": False, "reason": "mapped-library inspection unavailable"}
    paths = set()
    for line in maps.read_text(encoding="utf-8").splitlines():
        parts = line.split(maxsplit=5)
        if len(parts) == 6 and Path(parts[5]).name.startswith("libicu"):
            path = Path(parts[5])
            if path.is_file():
                paths.add(path.resolve())
    return {"available": bool(paths), "files": [
        {"fileName": path.name, "sha256": admitted.sha256(path), "sizeBytes": path.stat().st_size}
        for path in sorted(paths)]}


def atomic_json(path, value):
    """Replace one owned evidence file; an interrupted write leaves prior evidence."""
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    temporary.replace(path)


class ProgressRecorder:
    """Persist completed stage metrics at each boundary, outside measured windows."""
    def __init__(self, recorder, path):
        self.recorder, self.path = recorder, path

    @contextmanager
    def measure(self, name, **metadata):
        # Start marker precedes timing; the stage itself remains incomplete.
        progress = self.recorder.snapshot()
        progress["activeStages"].append(name)
        atomic_json(self.path, progress)
        try:
            with self.recorder.measure(name, **metadata):
                yield
        except BaseException:
            try:
                atomic_json(self.path, self.recorder.snapshot())
            except OSError:
                pass  # Keep the original pipeline failure if evidence disk is full.
            raise
        else:
            atomic_json(self.path, self.recorder.snapshot())


def run_profile(args):
    pool = Path(args.pool).resolve()
    report_path = Path(args.pool_report).resolve()
    registry = Path(args.registry).resolve()
    # Refuse accidentally starting the full retained corpus. Raising the limit
    # is an explicit diagnostic decision, not sampling or bypassing validation.
    if args.input_scope == "full-saved-input" and args.measurement_mode != "stages":
        raise ValueError("full-saved-input requires stages mode for durable progress evidence")
    if args.runs < 1 or args.runs > 5 or args.max_input_bytes < 1:
        raise ValueError("runs must be 1..5 and max-input-bytes must be positive")
    if pool.stat().st_size > args.max_input_bytes:
        raise ValueError("input exceeds diagnostic byte limit; use a bounded pool or explicitly raise --max-input-bytes for an authorized full-saved-input diagnostic")
    output = Path(args.output_dir).resolve()
    if output.exists():
        raise ValueError("output directory must be new; completed or interrupted evidence is never overwritten")
    for source in (pool, report_path, registry):
        if output == source or output in source.parents:
            raise ValueError("output directory must not contain an input")
    runtime_configuration = {key: os.environ.get(key) for key in ("IKNA_ICU_LIBRARY", "IKNA_ICU_VERSION")}
    initial_pins = {name: admitted.sha256(path) for name, path in
                    (("pool", pool), ("poolReport", report_path), ("registry", registry))}
    pipeline = pipeline_identity()
    output.mkdir(parents=True)
    evidence = None
    try:
        evidence = {"profileVersion": 1, "status": "incomplete", "publicationSafe": False,
                    "inputPins": initial_pins, "pipelineSha256": pipeline,
                    "inputBytes": pool.stat().st_size,
                    "inputScope": args.input_scope,
                    "settings": {key: getattr(args, key) for key in
                                 ("runs", "max_input_bytes", "quality_policy", "max_deck", "min_deck", "thin_deck", "measurement_mode")},
                    "environment": {"python": platform.python_version(), "platform": platform.platform(),
                                    "machine": platform.machine(), "processor": platform.processor(),
                                    "logicalCpuCount": os.cpu_count(), "sqlite": sqlite3.sqlite_version},
                    "measurementContract": {
                        "instrumentation": "cProfile; timings include instrumentation overhead",
                        "elapsed": "admitted.build_report only; selected output included, diagnostic report/preview writes excluded",
                        "inputGuard": "source-file bytes only, not a bound on decompressed bytes, rows, memory or runtime",
                        "boundaries": "cumulative calls overlap; do not sum stages",
                        "allocation": "contextChoice includes evidence pruning; allocation loop is not isolated",
                        "memory": "process lifetime peak RSS only; no stage-local peaks measured",
                        "reuse": "same process and filesystem; database recreated and all input checks repeated",
                        "cold": "OS page cache is uncontrolled; first run is not a proven cold-cache run",
                        "scope": "bounded input results cannot predict full corpus cost or classifier accuracy"},
                    "runs": []}
        if args.measurement_mode == "stages":
            evidence["measurementContract"].update(
                instrumentation="explicit stage contexts and sampled RSS; no cProfile; atomic progress serialization/fsync at each boundary adds overhead",
            progress="checkpoint IO is outside each proxied stage window but included in total admitted.build_report elapsed; nested parent windows would include child checkpoint IO; not a baseline engine speed comparison with earlier runs",
                boundaries="explicit disjoint stage windows; overhead/bookkeeping outside windows remains in total",
                allocation="isolated target ordering/context allocation loop per level, excludes pair-evidence pruning/output",
                memory="sampled whole-process RSS within each stage window; observed lower bound, not allocation accounting")
        override = os.environ.get("IKNA_ICU_LIBRARY")
        if override:
            evidence["environment"]["icuRuntimePin"] = {
                "fileName": Path(override).name, "sha256": admitted.sha256(Path(override)),
                "requiredVersion": os.environ.get("IKNA_ICU_VERSION")}
        if args.input_scope == "full-saved-input":
            evidence["measurementContract"]["scope"] = "full supplied saved pool; diagnostic only, no acceptance, publication or extrapolation to other corpora"
        evidence["measurementContract"]["resume"] = "partial SQLite/output is diagnostic evidence only, not resumable selection state"
        atomic_json(output / "PROFILE.partial.json", evidence)
        reference = None
        for index in range(args.runs):
            work = output / f"run-{index + 1:02d}"
            work.mkdir()
            command = ["--pool", str(pool), "--pool-report", str(report_path), "--registry", str(registry),
                       "--staging", str(work / "selection.sqlite3"), "--json", str(work / "selection.json"),
                       "--markdown", str(work / "selection.md"), "--preview", str(work / "preview.jsonl.gz"),
                       "--samples", str(work / "samples.md"), "--selected-output", str(work / "selected.jsonl.gz"),
                       "--quality-policy", args.quality_policy, "--max-deck", str(args.max_deck),
                       "--min-deck", str(args.min_deck), "--thin-deck", str(args.thin_deck)]
            selection_args = admitted.parser().parse_args(command)
            def write_diagnostics():
                # Variable measurements never enter the deterministic selection report.
                (work / "selection.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                selection.write_preview(str(work / "preview.jsonl.gz"), preview)

            atomic_json(work / "STAGE-PROGRESS.json", {"status": "incomplete", "activeStages": [], "stages": {}, "resumable": False, "publicationSafe": False})
            stage_metrics = None
            profiler = None
            if args.measurement_mode == "stages":
                with StageRecorder() as recorder:
                    start = time.perf_counter()
                    progress_recorder = ProgressRecorder(recorder, work / "STAGE-PROGRESS.json")
                    report, preview = admitted.build_report(selection_args, metrics=progress_recorder)
                    elapsed = time.perf_counter() - start
                    with progress_recorder.measure("diagnostic_output"):
                        write_diagnostics()
                stage_metrics = recorder.report()
                atomic_json(work / "STAGE-PROGRESS.json", recorder.snapshot())
                if stage_metrics["status"] != "complete":
                    raise ValueError("stage measurement did not complete")
            else:
                profiler = cProfile.Profile()
                start = time.perf_counter()
                report, preview = profiler.runcall(admitted.build_report, selection_args)
                elapsed = time.perf_counter() - start
                write_diagnostics()
            identity = {"reportSha256": canonical_hash(report), "previewSha256": canonical_hash(preview),
                        "selectedSha256": report["selectedMaterial"]["sha256"],
                        "selectedLogicalSha256": report["selectedMaterial"]["logicalSha256"]}
            if reference is not None and identity != reference:
                raise ValueError("repeated selection changed canonical output; no completed profile written")
            reference = identity
            evidence["runs"].append({"run": index + 1, "condition": "first-process-run" if index == 0 else "repeated-same-process",
                                     "elapsedSeconds": elapsed, "processLifetimePeakRssBytes": process_peak_rss(),
                                     "boundaries": boundary_stats(profiler) if profiler is not None else None,
                                     "stageMetrics": stage_metrics, "outputIdentity": identity,
                                     "stageCounts": report["stage"], "summary": report["summary"],
                                     "segmentation": report["selectionEnvironment"]["segmentation"],
                                     "stagingBytes": (work / "selection.sqlite3").stat().st_size,
                                     "selectedBytes": report["selectedMaterial"]["sizeBytes"]})
            atomic_json(output / "PROFILE.partial.json", evidence)
        evidence["environment"]["mappedIcuLibraries"] = loaded_icu_libraries()
        if runtime_configuration != {key: os.environ.get(key) for key in runtime_configuration}:
            raise ValueError("ICU configuration changed during profile")
        final_pins = {name: admitted.sha256(path) for name, path in
                      (("pool", pool), ("poolReport", report_path), ("registry", registry))}
        if initial_pins != final_pins:
            raise ValueError("input changed during profile; no completed profile written")
        if pipeline != pipeline_identity():
            raise ValueError("pipeline changed during profile; no completed profile written")
        evidence.update(status="complete-full-saved-input-diagnostic" if args.input_scope == "full-saved-input" else "complete-bounded-diagnostic", outputsEqualAcrossRuns=True if args.runs > 1 else None)
        temporary = output / "PROFILE.json.tmp"
        temporary.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        temporary.replace(output / "PROFILE.json")
        return evidence
    except BaseException as error:
        partial = evidence or {"publicationSafe": False, "runs": []}
        partial.update(status="failed", resumable=False,
                       failure={"type": type(error).__name__, "message": str(error)[:2000]})
        try:
            atomic_json(output / "PROFILE.partial.json", partial)
        except OSError:
            # Preserve the original failure; the independent guard also records exit.
            pass
        raise


def parser():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pool", required=True)
    ap.add_argument("--pool-report", required=True)
    ap.add_argument("--registry", default=str(admitted.DEFAULT_REGISTRY))
    ap.add_argument("--output-dir", required=True)
    ap.add_argument("--input-scope", required=True, choices=("fixture", "bounded-saved-input", "full-saved-input"))
    ap.add_argument("--measurement-mode", choices=("cprofile", "stages"), default="cprofile")
    ap.add_argument("--runs", type=int, default=2)
    ap.add_argument("--max-input-bytes", type=int, default=64 * 1024 * 1024)
    ap.add_argument("--quality-policy", choices=("legacy-v1", "boundary-diversity-v2"), default="boundary-diversity-v2")
    ap.add_argument("--max-deck", type=selection.core.v2_deck_budget, default=selection.core.V2_MAX_DECK_TARGETS)
    ap.add_argument("--min-deck", type=int, default=40)
    ap.add_argument("--thin-deck", type=int, default=1000)
    return ap


def main(argv=None):
    args = parser().parse_args(argv)
    result = run_profile(args)
    print(json.dumps({"status": result["status"], "runs": len(result["runs"])}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
