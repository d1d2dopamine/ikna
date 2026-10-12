# Independent post-success comparison

Do not invoke until the Round 33 guard has finished successfully and the complete candidate PROFILE.json exists. The helper checks the successful guard first. It never opens, hashes or modifies SQLite, never runs selection, and never modifies producer/source files.

Run with the pinned project virtual environment:

    /workspace/scratch/8105054a3211/ikna-venv/bin/python -B /workspace/scratch/8105054a3211/ikna-results/round33-isolated-full-candidate/compare_candidate_control.py

Success writes the new `CANDIDATE-CONTROL-COMPARISON.json`; failure writes a separate `.failed.json` when possible. Existing output evidence is not overwritten. Tiny tests passed: 10 tests in 0.004 seconds; see `COMPARISON-HELPER-TESTS.log`.

The complete current report may differ from Round 30 at exactly `/selectionPipelineSha256/selection_policy.py`, with the specific reviewed old/new hashes compiled into the helper. There is no broad allowance for other source fingerprints, environment changes, selected bytes, counters, decks or quality counts. All report differences are recorded before this gate. Other producer profile contracts and source/data inventories are checked separately; normal source must remain unchanged and the isolated tree must differ only at that policy file. Candidate chooser AST must equal the reviewed prototype and all remaining policy AST must equal original source. Pins are verified again before success.

The candidate gzip is read completely, including CRC/trailer validation, with bounded 4 MiB chunks. Raw/compressed and logical SHA-256, byte counts and membership-line count must equal candidate report, Round 30 control and its historical selected identity. Canonical report hashes bind both profiles to their reports. Stage wall/CPU/sampled RSS and whole-run elapsed/RSS comparisons are recorded separately from equality checks.

This is one full candidate invocation versus one control invocation under shared-host and uncontrolled cache conditions. The guards have different explicitly authorized disk floors. Timing ratios are observed comparisons, not calibrated repeat-run forecasts. Structural/output equality does not admit content, certify semantic quality, integrate the optimization or authorize publication.

Only small new comparison artifacts are produced. No full evaluator rerun or large temporary file is required.
