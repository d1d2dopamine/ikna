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

Developer Mode always bypasses product restrictions. There is no second switch to
turn this on: entering the developer profile is the explicit consent to test past
history maturity, today's plan, time-of-day rules, Governor safety gates, Browse
credits, cooldowns and usage limits. This keeps the mode useful even when the
developer cannot naturally reproduce the required learner state.

Forced access must not change the production policy result. The ordinary blockers
are still calculated and kept visible to the developer as diagnostics; they simply
do not prevent entry while the developer profile is active. Tests and synthetic
scenarios can still assert the production verdict directly. Technical failures such
as a database error, missing content or a failed migration are not product
restrictions and must not be masked by Developer Mode.

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

The settings surface should expose scenario selection, deterministic reseed, the
fact that product restrictions are bypassed, and a way back to the normal profile.
More granular diagnostic controls belong in a technical surface only if a real
debugging need appears.
