# Two files that are too big, and the split that does not break them

Status: planned, not started. Written down because the risky part of a split is
not the typing, it is deciding what may not move.

The two files:

| file | lines | what makes it hard |
| --- | --- | --- |
| `data/repo/LearningRepository.kt` | ~1,290 | holds the single write lock; every invariant about the day's plan lives inside it |
| `ui/settings/SettingsScreen.kt` | ~1,710 | one composable per setting, in one function, with the explanations that make the settings honest |

Neither is disorganised. Both are what happens when a file keeps being the right
place to add the next thing.

## LearningRepository

What it does today, in three groups that barely touch each other:

1. **The day's plan.** `ensureDailyPlan`, `collectSignals`, `withNightRule`,
   `introduce`, `addExtra`, the governor call, the plan row.
2. **Answering.** `answer`, undo, the card write, the review row, the day's
   counters.
3. **Reading.** Stats, the governor log, the digest, everything the widget and
   the stats screen ask for.

The split: `DailyPlanService`, `AnswerService`, `StatsService`, with
`LearningRepository` kept as the facade so no caller changes in the same commit
as the move.

The rules that a split must not quietly break:

- **One mutex, one owner.** `writeLock` exists because the plan may be decided
  once per day and a plan decided twice is the counter bug that `daily_plan` was
  introduced to fix. If it becomes two locks, that bug comes back in a form that
  only appears under a race. `DailyPlanService` and `AnswerService` must take the
  *same* lock instance, injected, not created.
- **`StatsService` takes no lock at all.** Reads must not be able to block an
  answer. If a read needs the lock to be correct, the read is wrong.
- **The plan only shrinks.** Nothing outside `DailyPlanService` may write
  `daily_plan`.
- **`reviews` is append-only.** Undo stays an inserted retraction row with
  `undoOf`, never an edit or a delete. `AnswerService` is the only writer.
- **Move first, change nothing.** Two commits: one that moves code and passes
  the existing tests untouched, one that changes behaviour. A refactor that
  fixes something on the way is a refactor nobody can review.

## SettingsScreen

One file, one function, and the prose that makes each switch understandable. The
prose is the reason it is long and it is also the reason it is worth keeping.

The split is by section, not by widget: language, theme and font, the day, the
load, voice, decks and packs, export and restore, updates, diagnostics. Each
becomes its own file with its own explanations; `SettingsScreen` becomes the
list of sections and the scroll container.

What must not change on the way:

- Every existing string key. `Strings*.kt` are 878 lines each across six
  languages, and a renamed key is a missing translation in five of them.
- The order of the sections. It is the order the user has already learned.
- The explanations. Moving them is fine, shortening them is a different
  decision that has nothing to do with file length.

While in there, three keys are worth renaming from `set.013`, `set.047`,
`set.138` to something readable — but as a separate commit, across all six
language files at once, with `StringsCatalogTest` as the check that nothing was
lost.

## Why this is written down instead of done

A move of this size touches every file that talks to either of these two, and
the only honest way to verify it is to compile it and run the test suite. That
belongs on a machine with the Android toolchain, in two reviewable commits, not
bundled with feature work.
