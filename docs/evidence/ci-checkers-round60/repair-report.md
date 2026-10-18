# Round60 CI checker repair

## Result
All 12 commands in the exact `grading.yml` **Verify text, fixtures and additive migrations** step pass. This is offline Python/SQLite/source-contract evidence, not a Kotlin build, hosted CI run, or physical UI acceptance.

## Changed files
- `tools/check_grading.py`: bind tracker keys to the actual `reviewSignals = remember(...) { ReviewSignalTracker() }` declaration; accept Round59 raw queued-card identity, retain loading/deck/reload/index identity; separately preserve invalidation and keyboard routing guards. Add 25 negative mutation cases (5 missing keys, 19 guard/routing/constructor changes, 1 unrelated-declaration decoy). Add shared production-schema version reader and support omitted empty index arrays in committed newer schemas.
- `tools/grading/check_full.py`: execute current DAO SQL against the committed production schema (14), preserving historical migration tests. Verify equal-timestamp event-ID/legacy-ID ordering. Update current normalized-duration accounting boundary with four negative mutations and pin current restore replay scheduler routing.
- `tools/check_optimizer.py`: bound migration SQL extraction to 7→8, preserving historical shape/value assertions. Use production schema for current DAO query; verify equal-timestamp event-ID/legacy-ID ordering.

No production Kotlin, UI, corpus, schema, workflow, version, or asset files changed by this repair.

## Baseline and final checks
- Original `check_grading.py`: 10 tests, 1 failure (stale current-card remember expression).
- Original `grading/check_full.py`: 8 tests, 1 failure + 1 error (stale raw duration; old schema missing eventId). Correcting the first assertion exposed the stale restore scheduler receiver too.
- Original `check_optimizer.py`: 9 tests, 2 failures + 1 error (migration extraction spill; old schema missing eventId).
- Final checker totals: 11 + 9 + 9 tests, all passing.
- Exact 12-command workflow step: all exit 0, see `python-ci-gate-final.log`.
- Whitespace diff check: no diagnostics; archive has no Git metadata, so comparisons use `git diff --no-index --check` against captured originals (exit 1 denotes differences).

## Evidence
Packaged evidence includes original failing checker logs, final per-checker logs,
the exact workflow-step final log, the three final unified patches, application
source preservation and the hash summary. Intermediate diagnostics remain separate
from this compact release evidence. The application source was not rebuilt here.
