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

Developer Mode bypasses product restrictions by default. Settings -> Rare ->
DEV tools provides **Apply ordinary restrictions**. Turn it on to exercise the
same product gates as a normal learner; turn it off to reach features without
waiting for history maturity, a completed plan, credits or the right time of day.
The setting belongs to the isolated DEV DataStore, is omitted from learner settings
exports, and cannot grant an override to a REAL profile. It is read by the shared
access boundary on each operation, without a process restart or policy fork.
Reseeding DEV clears its settings and returns the switch to the default.

The switch does not rebuild an existing daily plan or change Governor/Scheduler
rules. Forced classic sessions can use already scheduled cards when today's
pending queue is empty; this does not add them to the daily obligation. Real
answers still take the ordinary scheduling/history path, inside DEV only.
Missing installed content/schedules are reported by the ordinary empty state,
not replaced with fabricated content or answers.

Forced access must not change the production policy result. The ordinary blockers
are still calculated and kept visible to the developer as diagnostics; they simply
do not prevent entry while the developer override is enabled. Tests and synthetic
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

The shared DEV tools panel on Android and desktop provides:

- installed-deck selection and direct navigation to actual classic cards, Browse,
  statistics, Catalogue and search;
- a read-only snapshot with timestamp, current Governor preview (reason/new
  allowance/capacity), stored plan reason and pending/total, due/backlog,
  history row count, retained retired-mode rows and override status;
- refresh and copy-to-clipboard actions, plus the ordinary-restrictions switch.

Inspection is serialized with repository writes, and does not create a plan,
settle credits, introduce cards or append reviews. The current Governor preview
uses stored load settings; it may differ from a plan committed earlier today.
Opening a feature follows its actual repository path: Browse checks can create
that day's plan and settle credits, and this distinction is stated in the panel.
Cancelled operations propagate cancellation; failed actions show an error and can
be retried. Copying diagnostics only writes the local clipboard.

Existing deterministic reseed controls and return-to-normal profile controls remain
below the tools. This round adds no synthetic scenarios. See
[ROUND-0.12-11.md](ROUND-0.12-11.md) for scope and verification limits.
