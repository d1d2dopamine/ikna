# Round 56 — optional hard-card review

Status: completed local stage, 2026-10-17; physical Android/Windows acceptance remains open.
Baseline: sealed Round 55, SHA-256
`4e047cc98db8d6ebcda8cad241652c01bb8499765c5bdd6f8bd65786783d1f7b`.

The owner's 2026-10-17 decision permits a separate voluntary hard-card batch after
the main lesson. See [OPTIONAL-HARD-REVIEW-0.12.md](OPTIONAL-HARD-REVIEW-0.12.md)
for behavior, durable accounting and compatibility constraints.

## Acceptance

- Existing global required-plan completion gates entry without plan creation.
- At most five eligible learned hard targets; deterministic scope/suppression
  filtering and deduplication; no automatic repeats or new cards.
- Ordinary FSRS grading; factual totals include answers once, required completion
  and earned-new credit exclude optional answers across later ordinary activity.
- Undo, replay, export/restore, profile transfer and incremental sync retain purpose.
- Null/default legacy bytes and event identities remain exact; unknown purpose
  and incompatible peers reject safely.
- Explicit schema 13→14 migration and retained profile schema 12/13→14 paths.
- Stale batches after day/reset/removal/suppression fail without partial writes.
- Android and desktop wiring, focused and full suites/build checks reported from
  actual terminal evidence; physical validation remains separate.

## Stop conditions

Do not weaken bilateral sync approval, overwrite event identities, reinterpret
unknown purpose, silently credit optional practice as required work, or hide a
failed migration behind destructive fallback. A failed assertion remains a
failure until a justified fix passes the unchanged acceptance contract.

## Focused evidence (before final desktop presentation correction)

Run1: shared58 passed; desktop73 passed and one new positive-control fixture
failed because its fresh target lacked token metadata. The existing selector
correctly refused admission. Added technical token metadata only, retaining both
no-credit and positive ordinary-credit assertions.

Run2:149 cases, zero failures/errors/skips: shared58 unchanged cached results,
desktop74 and Android17 executed. Legacy byte fixtures were captured from sealed
Round55 compiled classes. Genuine schema13 migration preserves stream/prefix,
consent and checkpoint before optional answer/Undo transfer.

After run2, one desktop null-replacement-batch presentation branch was corrected
to reload normal counters before displaying completion, with a structural
regression. Final full suites and builds must verify this revised source.

## Final frozen-source result

Full four-target suite:1,174 cases,1,172 passed, two unchanged pointer-test skips,
zero failures/errors. Shared desktop147; desktop319 (317 passed); shared Android
local JVM140; app local JVM568. All461 compile-input hashes remained unchanged
across tests and builds. Full desktop includes both independent-process277-event
crash/restart tests from55. Source-contract UI checks establish wiring, not live
Compose/accessibility behavior on physical devices.

Standard debug APK and Linux distributable builds passed. APK v2 signature
verified against the unchanged signing identity. Packaged Linux selftest passed
in a fresh isolated home: schema14, quick_check=ok, zero decks/cards. Five static
checks passed: localization (seven languages/859 keys), classic cards, actual
schema14 wipe parity, text normalization and small-viewport source contracts.
See [evidence](evidence/optional-review-round56/TEST-RESULTS.json),
[builds](evidence/optional-review-round56/BUILD-SMOKE.json) and
[legacy byte provenance](evidence/optional-review-round56/LEGACY-BYTE-PROVENANCE.json).

No corpus payload, learner database, generated pairing credential or build output
is included in the source ZIP. Existing release version labels are unchanged;
this working round does not publish a release.
