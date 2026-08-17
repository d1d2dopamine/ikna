# Load Governor

The governor answers exactly one question, once per day: **how many new chunks may be
introduced?** Not "which cards are due" (that is FSRS) and not "what should the user study"
(that is the selector).

## Inputs

| Signal | Meaning |
| --- | --- |
| `dueToday` | cards already scheduled for today |
| `forecastAvg3d` | average daily due count over the next 3 days |
| `backlog` | overdue cards sitting in the amnesty pool |
| `accuracyRecent` | success rate over the last 100 reviews |
| `daysSinceLastSession` | 0 if studied today |
| `activityRatio` | how much of the last week was used, 0..1 |
| `daysSinceStart` | days since the first session ever |
| `reviewsDoneToday` | used for the post-skip warm-up gate |
| `cleanDays` | consecutive days where the day's plan was completed |
| `newIntroducedLastWeek` | new chunks in the last `safetyValveDays` days; feeds the safety valve |
| `totalReviews` | 0 means a fresh install |
| `daysSinceReturn` | days since the first session after the last real absence, or null |
| `overheated` | the last day of work was far above the median and ended badly |

## Decision

```
capacity   = targetDailyReviews                (returnModeCapacity while in return mode)
capacity  *= overheatCapacityShare             (only after an overheated day)
projected  = max(dueToday, forecastAvg3d) + backlogWeight * backlog
headroom   = capacity - projected
newCeiling = clamp(capacity * newCeilingShare, 1, maxNewCeiling)
allowedNew = clamp(headroom / costPerNew, 0, min(effectiveMaxNew, newCeiling))
```

`effectiveMaxNew` is `maxNewPerDay`, raised by the accelerator below once the
settling window has passed.

`newCeiling` is the second ceiling, and it answers a different question.
`headroom` asks whether there is room today; `newCeiling` asks whether anything
should be left for tomorrow. A quarter of the day, scaled to the norm rather than
fixed, because four new chunks on a fifteen-card norm is not the same day as four
on sixty. It is never zero: a day with nothing unfamiliar in it is the day this
app turns into a chore.

**Where `targetDailyReviews` comes from.** From the load switch in settings, read
out of storage at the moment the plan is built. It used to arrive only through a
settings flow that some screen had to be collecting, so a plan built by the
nightly worker or by a cold start could use the 40 from `governor.json` while the
user's own norm said 15 — a day a third too big, with nothing on any screen that
could explain it.

A fresh install (`totalReviews == 0`) skips all of this and gets `maxNewPerDay`
unconditionally: there is nothing to forecast yet.

`costPerNew` defaults to 4.0. This is the part every other app gets wrong: one new chunk is not
one card today, it is roughly four reviews over the following week. Treating new material as
free is what produces the avalanche three weeks in.

## Hard gates (force allowedNew = 0)

Checked in this order; the first one that matches wins.

| Gate | Condition |
| --- | --- |
| `RETURN_MODE` | `daysSinceLastSession >= returnModeGapDays`, or `daysSinceReturn < returnModeDays` |
| `OVERHEATED` | the last day of work ran hot — see below |
| `BACKLOG_LIMIT` | `backlog > backlogHardLimit` |
| `LOW_ACTIVITY` | `activityRatio < minActivityRatio` |
| `POST_SKIP_WARMUP` | `daysSinceLastSession >= 2` and `reviewsDoneToday < warmupReviewsAfterSkip` |
| `LOW_ACCURACY` | `accuracyRecent < minAccuracy` |

The post-skip gate is the behavioural core: after a missed day, new material is *earned* by
attention, not handed out to help you "catch up". Gamified apps do the opposite.

**Two days, not one.** A day's plan is built before that day's first answer
exists, so `daysSinceLastSession` is 1 on every ordinary morning that follows an
ordinary evening. Comparing against 1 fired the gate on every normal day of use
and left the safety valve as the only source of new material — one chunk a week.
A skipped day is a day with no answers in it, which is two calendar days since
the last one.

**Return mode covers the days after the return, not just the day of it.** The gap
itself opens it; `daysSinceReturn` keeps it open for `returnModeDays` afterwards.
Being handed a full day on the second evening back is how a return becomes the
next absence.

## Earned inside the session

The governor rules once, in the morning, and on a day whose queue already fills
the capacity it correctly rules that there is no room. Then the user answers
everything, faster than the forecast expected — and the app has nothing
unfamiliar left to show them. The reward for finishing was that finishing changed
nothing, which is the exact treadmill this project exists to avoid.

So the budget is re-read as the session runs. Every `earnedNewPerReviews` reviews
past the day's obligation open one more chunk, up to `newCeiling`:

```
beyond = reviewsDoneToday - (plannedTotal - extraRequested)
target = min(allowedNew + beyond / earnedNewPerReviews, newCeiling)
```

`earnedNewPerReviews` equals `costPerNew` on purpose: the chunk is paid for at
what it will cost over the coming week, so nothing is borrowed from tomorrow.
Earned chunks are recorded as extra rather than as plan, so finishing the day
still means what it meant when the day started.

Gates are never overridden this way. If the morning's verdict was about the state
of the user — a pile, a quiet week, poor accuracy, a return, an overheated day —
nothing is earned however much work is done. Only `OK`, `NO_HEADROOM` and
`FIRST_RUN` can be reopened, because those three are about room.

## Overheating

Everything else here protects a queue from growing too fast. Nothing protected
the user from being spent, and one heroic evening is how the next four days get
skipped.

Three things have to agree, because any one of them alone is an ordinary day: the
last day of work was more than `overheatRatio` times the median day, **and**
either its accuracy fell `overheatAccuracyDrop` below the usual or its plan was
abandoned. A big, accurate, finished day is a good day and is left alone.

The median is what makes this readable: one outlier is exactly what a median does
not move, so "far above the median" means that day stood out rather than that the
habit has grown. When it fires, new material stops first and the day itself
shrinks to `overheatCapacityShare` of the norm — reviews still arrive, in a
smaller plan, and the session says why.

The sharper signals — where inside a session accuracy fell away, how the gaps
between answers grew — need a per-answer record that does not exist yet. Reading
the three columns that are already trustworthy is better than inventing a fourth
badly.

## Safety valve

If nothing was introduced in the last `safetyValveDays` days (7 by default — the window is
counted by the repository, not here), exactly one new chunk is released regardless of
every gate. Without this the governor can latch at zero forever, novelty disappears, and the
app becomes the treadmill it was built to avoid.

The gate the valve overrode is kept on the decision and written to the log beside
it (`SAFETY_VALVE/LOW_ACCURACY`). The valve used to overwrite the reason it was
overriding, so the one event most worth investigating — something is wrong, and a
chunk is being handed out anyway — recorded nothing about itself.

## Accelerator

After `accelerateAfterCleanDays` clean days with accuracy above `accelerateMinAccuracy`,
`maxNewPerDay` grows by `accelerateStep`, capped at `maxNewCeiling`, and still bounded by the
day's `newCeiling`. Load rises to meet current form without being asked. That threshold used to
be a literal in the governor, two lines from the configured numbers that mean the same thing,
where no amount of tuning could reach it.

## Amnesty

Overdue cards are not queued. They move to an amnesty pool and are drip-fed at
`amnestyQuotaRatio` (20%) of each session, while FSRS honestly recomputes their stability from
the elapsed time. The session counter is clamped to `capacity`, so the UI is structurally
incapable of displaying a four-digit number of pending cards.

## A wrong card is not a wrong answer

Every input on this page comes from the review log, so anything that writes to
that log moves the governor. A card the deck simply got wrong used to be answered
*forgot*, which meant one bad card lowered `accuracy`, and `accuracy` below
`minAccuracy` is a hard gate: no new material at all. A deck with a handful of
invented cards could therefore shut the day down while looking like a person who
was struggling.

**This card is wrong** writes nothing. No review, no rating, no lapse. The card
leaves the rotation, the plan is rebuilt without it, and if it had been introduced
today the day's new-material count is given back so the room it took is usable
again. Nothing the governor reads changes, because nothing about the learner
changed -- the deck did.

The cards taken out this way are not deleted and can all be put back from settings.
If they are, the plan is invalidated so the day is decided again with them in it.

## Observability

Every decision writes a `governor_log` row with all inputs, the computed headroom, the result
and a reason code. Without this the valve is impossible to debug when it declines to hand out
new material.

## Day boundary

`dayStartHour` (default 4) decides which day an answer belongs to, and it is the same boundary
everywhere: the daily plan, `daily_stats`, the activity ratio the governor reads, the activity map
on the progress screen, and the replay that rebuilds stats from an imported log.

It also widens the night rule. `nightCutoffHour` alone said "nothing new after 23:00", which
reopened the gate at 00:00 — the worst possible time to meet a chunk for the first time. New
material is now blocked from `nightCutoffHour` until `dayStartHour`; reviews are never blocked.

The arithmetic lives in `dev.ikna.domain.time.DayBoundary`, which has no Android dependency and is
covered by `DayBoundaryTest` — date maths is easy to get subtly wrong and cheap to test.

It is also where intervals land. A due time is the start of the study day the interval falls in, not
the clock time of the answer plus the interval: otherwise a card answered at 23:00 comes back at
23:00 the next night, an evening session misses it, and the interval FSRS chose is stretched by up to
a day on every review. Snapping only ever moves a due time earlier, so nothing is hidden longer than
intended, and intervals are floored at one full day so it can never move one into the past
(`Scheduler.dueAt`, `SchedulerDueDayTest`).

## Everything new goes through `allowedNew`

`allowedNew` is the only authorisation for new material, and it covers every kind. Introducing a
fresh chunk is the obvious one; promoting an item to its next level — recognition to cloze to
production, when stability passes three weeks — is the one that is easy to miss, because it happens
in the answer path rather than in the daily plan. A promoted level is a question the user has never
been asked, so it spends the same budget and is written into `daily_stats.newIntroduced` the same
way; undo returns it. Otherwise a night session could mint new cards on a day the governor had
allowed none, and the measured norm would size the next day from a day that never happened. The rule
itself is `dev.ikna.domain.session.LevelPromotion`, kept separate and pure so it can be read in one
sitting and tested without a database.

## Serialised writes

Answering is a read-modify-write on one `daily_stats` row. Two fast swipes used to overlap and the
second write erased the first, so a card was answered but not counted — and that counter feeds the
measured norm, which feeds capacity, which decides how much new material the day gets. Writes that
touch card state, the review log and the day counter now go through a single mutex in
`LearningRepository`. Swiping fast is the normal speed of a good session, not misuse.
