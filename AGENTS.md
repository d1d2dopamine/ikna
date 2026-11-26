# Working on ikna

Read [CONTRIBUTING.md](CONTRIBUTING.md), [ARCHITECTURE.md](docs/ARCHITECTURE.md)
and the active working plan linked from CONTRIBUTING before editing. Read the
design/data/science document that owns the requested change. Use live repository
contracts and current owner decisions rather than historical checklist status.

## Invariants

- Keep review history append-only; preserve export/restore and deterministic replay.
- Add explicit Room migrations and commit schemas; never use destructive fallback.
- Preserve one learner memory per global exact target across deck memberships.
- Treat contexts as presentations/observations, not extra scheduling cards.
- Keep Scheduler, Governor, Target/Context/Transfer Policy and Grading distinct.
- Keep Browse exposures out of FSRS; isolate Developer Sandbox data/settings.
- Keep Catalogue licence/provenance gates fail-closed and experiments non-publishing.
- Preserve tracked signing identity and source assets; publish Catalogue index last.

Keep changes scoped, run relevant CONTRIBUTING checks and review the final diff.
Distinguish local checks from real-platform evidence. Publication and destructive
operations require authorization for the actual action.

## Repository skills

- [ikna-development](skills/ikna-development/SKILL.md) is the shared contributor
  protocol (including [agent-pitfalls.md](skills/ikna-development/references/agent-pitfalls.md)
  and [check-matrix.md](skills/ikna-development/references/check-matrix.md)).
  Workspace discovery is registered via `.agents/skills.json`.
- [ikna-owner-workflow](skills/ikna-owner-workflow/SKILL.md) adds the owner's
  personal workflow (Russian default, fast local checks without heavy Gradle,
  `gh` CI workflow, and optional ZIP packaging). Load it only when the owner
  explicitly selects it; do not impose it on other contributors.

Both skills live in source control. Tools without skill support should follow
this file, CONTRIBUTING and the same owning contracts.
