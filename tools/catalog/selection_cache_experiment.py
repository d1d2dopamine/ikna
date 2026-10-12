#!/usr/bin/env python3
"""Bounded analysis-cache experiment; no production selector cache is enabled."""
from __future__ import annotations
import json
from pathlib import Path

import selection_experiment as selection
import selection_profile as profiling
from selection_analysis_cache import run_cached_analysis


def run_experiment(args):
    parent_path = Path(args.pool_report)
    parent = json.loads(parent_path.read_text(encoding="utf-8"))
    parent_identity = {
        "poolSha256": parent["pool"]["sha256"],
        "poolReportSha256": profiling.admitted.sha256(parent_path),
        "registrySha256": parent["registrySha256"],
        "learn": parent["learn"], "meanings": parent["meanings"],
    }
    # This snapshot is only a key input. The ordinary admitted path still verifies
    # the actual complete supplied pool and every origin before this hook runs.
    original = selection.build_context_analysis
    events = []
    runtime = None

    def cached(db, collection, lang, ranks, function_top, batch_size=5000,
               japanese_guard=None, canonical_unique=False):
        nonlocal runtime
        current_runtime = {"segmentation": selection.prepare([lang])}
        version = current_runtime["segmentation"].get("icuVersion")
        if version:
            if runtime is None:
                mapped = profiling.loaded_icu_libraries()
                names = {"libicui18n": "main", "libicuuc": "uc", "libicudata": "data"}
                libraries = {}
                for row in mapped.get("files", []):
                    for prefix, key in names.items():
                        if row["fileName"] == prefix + ".so." + version:
                            libraries[key] = row["sha256"]
                runtime = {"icuLibraries": libraries}
            current_runtime.update(runtime)
        counts, diagnostic = run_cached_analysis(
            db, collection, lang, ranks, function_top, japanese_guard, canonical_unique,
            Path(args.cache_dir), parent["registrySha256"],
            lambda: original(db, collection, lang, ranks, function_top, batch_size=batch_size,
                             japanese_guard=japanese_guard, canonical_unique=canonical_unique),
            parent_identity=parent_identity, runtime_identity=current_runtime)
        events.append({"collection": collection, "lang": lang, **diagnostic})
        return counts

    if args.cache_mode == "enabled":
        selection.build_context_analysis = cached
    try:
        result = profiling.run_profile(args)
    finally:
        selection.build_context_analysis = original
    evidence = {
        "experimentVersion": 1, "status": "complete-bounded-cache-experiment",
        "publicationSafe": False, "productionCacheEnabled": False,
        "mode": args.cache_mode, "profileFile": "PROFILE.json", "cacheEvents": events,
        "outputIdentities": [run["outputIdentity"] for run in result["runs"]],
        "elapsedSeconds": [run["elapsedSeconds"] for run in result["runs"]],
        "mandatoryChecks": "unchanged complete supplied-input validation, staging and ranks; downstream evidence/allocation/output recomputed",
        "timingScope": "selection call includes cache key/integrity/import/write costs; profile provenance/final diagnostics separately outside call",
        "limits": "bounded same-input experiment; no full-corpus speedup, new quality claim or production cache admission",
    }
    path = Path(args.output_dir) / "CACHE-EXPERIMENT.json"
    temporary = path.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)
    return evidence


def parser():
    ap = profiling.parser()
    ap.description = __doc__
    ap.set_defaults(measurement_mode="stages")
    ap.add_argument("--cache-dir", required=True)
    ap.add_argument("--cache-mode", choices=("baseline", "enabled"), default="enabled")
    return ap


if __name__ == "__main__":
    evidence = run_experiment(parser().parse_args())
    print(json.dumps({"status": evidence["status"], "cacheEvents": len(evidence["cacheEvents"])}))
