# Developer Mode

Developer Mode is an isolated test profile for exercising application states that
would otherwise require weeks of real learner history. It is a development tool,
not a second learning profile and not a shortcut in normal learner data.

## Data isolation contract

The active data profile is chosen before the application dependency graph is
created.

- normal mode opens `ikna.db` and the normal settings DataStore;
- Developer Mode opens a separate database and a separate settings DataStore;
- repositories, Scheduler, Governor, Browse, statistics and FSRS operate on only
  the profile selected for that process;
- switching profiles requires a process restart so no repository or ViewModel can
  retain references to the previous profile;
- developer data is never copied or merged into normal learner history.

The developer database uses the same current Room schema, migrations, DAOs and
repositories as the real database. Do not create a fake repository implementation
for Developer Mode: the point is to exercise production code against controlled
inputs.

## Synthetic scenarios

Developer Mode can replace its own data with deterministic synthetic states.
Scenarios are reproducible fixtures rather than feature flags. They may create
synthetic packs, cards, reviews, statistics, plans and Governor evidence needed to
reach a state naturally.

Current scenario classes cover an empty profile, early history, mature history,
Browse-ready history, statistics-rich history and a return after a long break.
Future scenarios should be added only when they represent a distinct product
state that is otherwise expensive or impossible to reproduce manually.

Synthetic history must never be inserted into the normal profile. Reset/reseed is
allowed to be destructive only inside the developer database.

## Restrictions and forced access

Developer Mode has two different testing paths and they must remain distinct:

1. **Production-policy testing.** Synthetic history is evaluated by the normal
   policies with no override. A feature must become available for the same reason
   it would for a real learner.
2. **Forced-access testing.** The developer may ask the application to continue
   despite product restrictions such as history maturity, today's plan, time of
   day, Governor safety gates, Browse credits or cooldowns.

Forced access must not change the production policy result. The ordinary blockers
are still calculated and kept visible to the developer; the override only changes
whether the developer may continue. Technical failures such as a database error,
missing content or a failed migration are not product restrictions and must not be
masked by Developer Mode.

Do not scatter `if (developerMode)` through policy code. Route overrides through
the shared developer-access boundary so new restricted features can reuse the same
contract.

## Background work and exports

Developer Mode does not schedule normal learner reminders, daily-plan workers or
automatic exports.

Manual developer exports are explicitly marked synthetic. Review restore already
rejects synthetic review records, and settings restore rejects a synthetic settings
snapshot. This keeps a developer bug-report file from becoming real learner state
by accident.

## UI contract

Developer Mode lives under Settings -> Rare and requires an explicit warning before
it is enabled. While active, a persistent `DEV` marker remains visible so the
current profile cannot be mistaken for normal learning.

The settings surface should expose scenario selection, deterministic reseed, a
single high-level "ignore restrictions" control, and a way back to the normal
profile. More granular override controls belong in a technical diagnostic surface
only if a real debugging need appears.
