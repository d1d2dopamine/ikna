# Verification matrix

Use the current `CONTRIBUTING.md` as the command authority. This matrix helps choose scope; it is not a substitute for live project instructions.

## Always after code changes

- Run the focused unit/contract test closest to the changed code.
- Run any repository checker that directly owns the changed surface.
- Review `git diff --check` and the final diff when working in a git checkout.
- Confirm no unrelated files changed.

## Shared learning logic / repositories

Run shared/desktop JVM tests and Android JVM tests when the environment supports them. Add focused tests for changed deterministic policy. If behaviour affects replay/restore, run the relevant restore/migration/replay tests too.

Read the current Gradle tasks and toolchain pins from CONTRIBUTING and CI. Use
available tooling; do not assume a committed wrapper jar exists. Distinguish
Android unit, desktop JVM, instrumentation and packaged application verification.

## Room / migrations

Minimum evidence:

- focused database/repository unit tests;
- migration tests from the relevant prior schema(s);
- committed `app/schemas` update;
- confirmation that destructive migration was not introduced;
- review of `reviews` handling for accidental UPDATE/DELETE/rebuild.

A local JVM-only green result does not replace the project's real Android migration test when that is required by CI.

## Catalogue tooling/data

Run the contract/tests corresponding to the changed stage: segmentation, ingestion, morphology, v2 contract/builder, v1-v2 parity, meta-info/readiness, selection, storage, provenance, or census as applicable.

Do not run a giant corpus rebuild when a unit/fixture test can validate the code change. Conversely, do not claim release-quality evidence from fixture-only tests when the plan requires a full evidence run.

Keep `publish=false` for experiments/previews unless publication itself is the explicit user task and all gates are satisfied.

## Localization/text

Run the project's text and localization checkers after adding/changing user-visible strings. Verify every supported locale expected by the current repository.

## UI / design

Run design/palette parity checks when touching shared visual primitives, palettes, or platform parity. For Android widgets, also inspect/test the minimum launcher size described by project docs.

Run `:shared:desktopTest` and `:shared:testDebugUnitTest` for the shared JVM UI
policy regressions, as well as the affected application suites/builds. Follow
CONTRIBUTING's memory-constrained build guidance; never overlap a hot-run session
and another Gradle build on the same limited machine. Static parity checks do not
replace compilation or the real pointer/keyboard smoke pass.

## Installer / CI / release metadata

Run the repository's NSIS/CI/version self-checks that cover modified files. Inspect `.github/workflows/release.yml` and all version consumers before declaring a release bump synchronized.

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
dependency. Run bundled scripts and check both success and refusal paths. Run
the text checker and review documentation links; application builds are not
required for a skills-only change that does not touch product/build code.
