# ikna project contract

This file contains durable guardrails, not release-status facts. The live repository is authoritative.

## Repository ownership map

- `shared/`: cross-platform learning logic, persistence/repositories, FSRS/Governor/grading/policies, shared UI/domain code.
- `app/`: Android application and Android-only integrations/tests/assets.
- `desktop/`: desktop entry point, integrations, tests and packaging.
- `tools/`: deterministic checks and offline Catalogue/build tooling.
- `docs/`: design contracts, science/evidence language, architecture, plans and release process.
- `.github/workflows/`: CI, release and Catalogue workflows.
- `app/schemas/`: committed Room schema history and migration evidence.

Confirm this map against `CONTRIBUTING.md` before relying on it.

## Learner-data invariants

1. The review log is the source-of-truth history and is append-only during normal operation and migrations.
2. Room migrations may add review fields but must not use destructive migration to make a schema mismatch disappear.
3. Rebuildable state can be regenerated from history; learner history cannot.
4. Restore/replay behaviour is part of persistence correctness. A change that works only for new installs is insufficient.
5. One exact global target must not gain duplicate independent learner memories merely because it appears in multiple decks/contexts.

Inspect the current comments and tests around `IknaDatabase`, `Migrations`, `RestoreRepository`, and related entities before persistence work.

## Learning-engine boundaries

Use the repository's current responsibility split in `docs/LEARNING-ENGINE.md`:

- Scheduler: when
- Governor: how much
- Target Policy: what
- Context Policy: which stored context
- Transfer Policy: unseen-context evidence
- Grading: what the response means

Do not collapse these into one opaque decision path. Experimental context/transfer evidence must not silently become a scheduling grade or second target memory.

## Catalogue identity and provenance

Keep separate:

- exact target identity;
- deck/collection membership;
- natural source contexts;
- learner scheduling state.

A source adapter must normalize into the common offline pipeline rather than leaking source-specific formats into runtime learning code.

For production data, preserve enough source/version/licence/attribution information for later audit. Unknown or unaudited sources should fail closed according to the live corpus policy.

## What must always be re-read

Never store these as permanent truth in the skill:

- current database version;
- current app/desktop version;
- active planning cycle and current task completion status;
- current Catalogue measurements/counts;
- admitted/rejected corpus decisions if the live decision record changes;
- exact test command list;
- release blockers;
- CI workflow requirements.

Read them from the repository before acting.
