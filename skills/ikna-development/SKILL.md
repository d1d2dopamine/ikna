---
name: ikna-development
description: Develop, review, debug, test, or plan changes to the ikna repository as a contributor. Use for Android, desktop, shared learning code, Room migrations, Catalogue v2, corpora, CI, localization, packaging, and release work. Follow the active repository plan, preserve learner history and global target identity, verify scoped changes, and review the final diff.
---

# Ikna Development

Use this contributor protocol with live repository contracts. Preserve learner
data and established behaviour unless the requested task explicitly changes them.
Keep versions, release status, corpus decisions and check commands in their owning
repository documents rather than copying them into this skill.

## Start a task

1. Locate the root containing `CONTRIBUTING.md`, `app/`, `shared/`, and `desktop/`.
   Read root `AGENTS.md` when present.
2. Read CONTRIBUTING, `docs/ARCHITECTURE.md`, the active working plan linked by
   CONTRIBUTING, and the document that owns the requested area.
3. Run `scripts/preflight.py <repo-root>` when code execution is available.
   Treat the output as a sanity report, never as build or test evidence.
4. Read the relevant plan stage, linked decisions and current tests before choosing
   an implementation. Distinguish historical evidence from live status.
5. Establish the narrowest relevant baseline when feasible. Identify the owning
   layer, expected files and invariants before editing.

Follow current user instructions and repository contracts over stale skill
snapshots. Mention material discrepancies; do not override a newer owner
decision with an older plan checkbox.

## Implement and verify

1. Bound the change to one coherent task. Avoid unrelated cleanup.
2. Inspect callers before changing shared APIs, persistence, identifiers,
   serialization, learning policy or release metadata.
3. Implement in the smallest owning layer. Prefer shared code for behaviour
   required on Android and desktop.
4. Add or update meaningful regression tests for changed behaviour. Do not add
   implementation-mirroring tests for an ordinary documentation edit.
5. Select checks using [check-matrix.md](references/check-matrix.md) and live
   CONTRIBUTING commands. Report unavailable verification explicitly.
6. Review `git diff --check` and the final diff: inspect version/schema drift,
   generated files, hard-coded strings, publishing flags, destructive SQL,
   unexpected deletions and unrelated formatting.
7. Report resulting behaviour, relevant checks and remaining limitations.
   Never describe a check or build as passed unless it actually ran.

Use the contributor's requested delivery method. Do not impose a ZIP, patch,
personal operating-system path or chat format on ordinary contributions.

Do not publish, tag, release, upload public Catalogue assets, rotate signing
material or perform destructive actions without authorization for that action.
Planning a release is separate from publishing it.

## Data and learning contracts

Read [project-contract.md](references/project-contract.md) before persistence,
scheduling, history, target/context identity or Catalogue work.

- Keep `reviews` append-only during normal operation and migrations. Represent
  undo through the existing additive retraction path.
- Never introduce `fallbackToDestructiveMigration()`.
- Keep explicit Room migrations and committed schemas in `app/schemas/`.
- Distinguish rebuildable state from irreplaceable learner history.
- Preserve one learner memory per global exact target across deck memberships.
- Treat contexts as source observations/presentations, not extra FSRS cards.
- Keep Scheduler, Governor, Target Policy, Context Policy, Transfer Policy and
  Grading responsibilities distinct.
- Keep Developer Sandbox data/settings physically separate from learner data.
  Preserve production verdicts while forcing developer access only to product
  gates, never technical failures.
- Keep Browse exposures separate from retrieval evidence and FSRS answers.

For an explicitly requested erase/reset feature, inspect its deliberate
destructive path. Do not reinterpret it as permission for destructive migrations
or incidental loss of learner history.

## Branch by affected area

### Room and persistence

Inspect the database, entities, DAOs, migrations, export/restore and tests.
For a production schema change, increment the version, add an explicit migration,
preserve history, export/commit the schema and run relevant migration tests.
Include upgrades from prior schemas and replay/restore implications.
Treat an unverified migration as incomplete.

### Learning policy

Read the owning learning-engine and science documents. Require deterministic
decisions for the same relevant history unless documented design says otherwise.
Version observations where replay needs their meaning. Do not let context/transfer
experiments silently change target identity, grades or FSRS intervals. Verify
direct policy behaviour and replay/restore effects.

### Catalogue and corpora

Read the active plan, `docs/CATALOGUE-V2.md`, `docs/SOURCES.md`, and the source
policy/runbook/decisions linked by the plan. Treat older release filenames as
historical references, not automatic execution instructions.

- Stop source-specific formats at the offline ingestion boundary.
- Use auditable human-source sentences; do not generate runtime study sentences
  or machine-translated/pivoted filler for missing source pairs.
- Keep licence and provenance gates fail-closed.
- Keep target identity, membership, contexts and learner state distinct.
- Keep experiments/censuses non-publishing unless publication is authorized and
  the active release gates have been met.
- Re-read admitted/rejected source decisions. Counts or a successful workflow
  alone cannot admit a source or prove content quality.
- Preserve the repository-defined fixed public Catalogue tag and index-last
  publication ordering.
- Respect content review/freeze before final storage decisions and context
  evidence history before Context/Transfer experiments.

For WikiMatrix census work, preserve score-sorted acquisition and explicit stop
reasons. Require the configured score floor or clean EOF for complete evidence;
label capped scans as lower bounds. Do not let intentional pipe closure hide
transport truncation.

For sharded frequency ranks, preserve counts and first-seen tie order. Prefer
compact count shards to raw candidate transfers. Verify equivalence to the
monolithic fixture path.

### UI and localization

Preserve the established visual language unless redesign is requested. Use the
localization system for user-facing text. Verify text/localization, applicable
design/palette contracts and Android/desktop implications. Check minimum widget
size, narrow layouts, font scaling and reduced motion where affected. Distinguish
source/static checks from actual device or packaged desktop evidence.

### Release, CI and packaging

Read `docs/VERSIONS.md`, build files, release workflows and active release gates.
Keep a version bump as a dedicated step; verify human version, Android versionCode,
desktop numeric version, About/installer surfaces and tag rules together. Do not
bump build metadata merely because planning moved to a new cycle. Keep application
release and Catalogue publication separate.

Preserve source-controlled signing identity and binary/schema assets in source
packages. Do not discard a tracked file because it resembles a credential or a
generated file. Use the source manifest and ignore rules to distinguish source
from machine-local clutter.

## Scope discipline and resources

Fix an unrelated defect in the same task only when it blocks the requested work
or causes the same failure. Otherwise record it in its owning plan. Do not rename
stable identifiers or refactor database ownership as incidental cleanup.

- [project-contract.md](references/project-contract.md): durable constraints and
  where to look up changing facts.
- [check-matrix.md](references/check-matrix.md): verification by affected area.
- `scripts/preflight.py`: read-only version/schema/active-plan report.

For tools without skill support, follow root AGENTS and the same repository
contracts. Keep owner-specific delivery rules in the separate owner workflow.
