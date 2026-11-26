# Verification matrix

Use the current `CONTRIBUTING.md` as the command authority. This matrix helps choose scope; it is not a substitute for live project instructions.

## Always after code changes

- Run the focused unit/contract test closest to the changed code.
- Run any repository checker that directly owns the changed surface.
- After any rename/move/split, run `git grep -n "<symbol>" -- shared/ app/ desktop/` across all three modules.
- Review `git diff --check` and the final diff when working in a git checkout.
- Confirm no unrelated files changed.

## Fast local pre-push gate (when full local Gradle is skipped)

These deterministic checks take a few seconds, require no Gradle daemon or hot
reload, and catch localization, parity, grading, DEX naming, and Settings layout
regressions before pushing to CI:

- `python tools/check_localization.py` — validates all 7 locale files and token parity.
- `python tools/check_text.py` — validates UTF-8/NFC encoding across repo text files.
- `python tools/check_design_parity.py` — enforces Android/Desktop design & feature parity contracts.
- `python tools/check_grading.py` and `python tools/grading/check_full.py` — enforces grading, replay, and schema contracts.
- `python tools/check_optimizer.py` — enforces FSRS optimizer safety and boundary contracts.
- `python tools/check_classic_cards.py` — enforces classic card DAO invariants.
- `python tools/check_android_ci.py` — catches non-DEX-safe Kotlin identifiers (e.g., `.` in backtick test names).
- `python tools/check_palettes.py` — validates palette contrast and readability contracts.
- `SettingsSourceContracts.java` (`javac --release 8` + `java dev.ikna.ui.SettingsSourceContracts`) — validates lazy Settings layout contracts.

Static checks never prove Kotlin compilation or runtime test pass. Report clearly
which static checks ran locally and what GitHub Actions CI still verifies.

## Shared learning logic / repositories

Run shared/desktop JVM tests and Android JVM tests when the environment supports them. Add focused tests for changed deterministic policy. If behaviour affects replay/restore, run the relevant restore/migration/replay tests too (`JournalReplayEngineTest`, restore/migration suites).

Read the current Gradle tasks and toolchain pins from CONTRIBUTING and CI. Use
available tooling; do not assume a committed wrapper jar exists. Distinguish
Android unit, desktop JVM, instrumentation and packaged application verification.

## LAN sync and pairing

Run `PairedLanTransportTest`, `ProfileConsentProtocolTest`, `DeviceSyncStatusTest`,
`DeviceSyncErrorTest`, and integration suites when JVM execution is enabled (or
verify via CI). Verify:

- VPN/virtual adapter ranking and multi-host fallback;
- protocol rejection and recovery after cancelled proposals;
- no secret, certificate, or raw peer address leakage in diagnostic strings.

## Room / migrations

Minimum evidence:

- focused database/repository unit tests;
- migration tests from the relevant prior schema(s);
- committed `app/schemas` update;
- confirmation that destructive migration was not introduced;
- review of `reviews` handling for accidental UPDATE/DELETE/rebuild.

A local JVM-only green result does not replace the project's real Android migration test when that is required by CI.

## Catalogue tooling/data

Run the contract/tests corresponding to the changed stage: segmentation, ingestion, morphology, v2 contract/builder, v1-v2 parity, meta-info/readiness, selection, storage, provenance, SHA-256 integrity, or census as applicable.

Do not run a giant corpus rebuild when a unit/fixture test can validate the code change. Conversely, do not claim release-quality evidence from fixture-only tests when the plan requires a full evidence run.

Keep `publish=false` for experiments/previews unless publication itself is the explicit user task and all gates are satisfied.

## Localization/text

Run `tools/check_localization.py` and `tools/check_text.py` after adding/changing
user-visible strings. Verify all 7 supported locales stay key-symmetric.

## UI / design

Run `tools/check_design_parity.py` and `tools/check_palettes.py` when touching
shared visual primitives, palettes, or platform parity. For Android widgets, also
inspect/test the minimum launcher size described by project docs.

Run `:shared:desktopTest` and `:shared:testDebugUnitTest` for the shared JVM UI
policy regressions, as well as the affected application suites/builds when local
Gradle is authorized. Follow CONTRIBUTING's memory-constrained build guidance;
never overlap a hot-run session and another Gradle build on the same limited
machine. Static parity checks do not replace compilation or the real
pointer/keyboard smoke pass.

## Installer / CI / release metadata

Run the repository's NSIS/CI/version self-checks that cover modified files.
Inspect `.github/workflows/release.yml` and all version consumers before declaring
a release bump synchronized.

When diagnosing CI failures, run `gh run list` and
`gh run view <run-id> --log-failed` instead of opening GitHub in a browser.

## Before release publication

Follow the active working plan linked from CONTRIBUTING and its Release Blockers,
plus `docs/VERSIONS.md` and release workflow gates. Re-read inherited Catalogue
decision records when the active plan references them. An application release
does not establish Catalogue publication readiness. A release requires broader
validation than an ordinary pull request.

## Repository skills and documentation

Validate skill frontmatter, relative resources and agent metadata. For the common
skill, verify that owner-specific delivery rules and a fixed release number are
absent. For the owner skill, verify explicit activation and the common-skill
dependency. Run bundled scripts (`preflight.py`, and `package_repo.py` when
packaging is touched) and check both success and refusal paths. Run
`tools/check_text.py` and review documentation links; application builds are not
required for a skills-only change that does not touch product/build code.
