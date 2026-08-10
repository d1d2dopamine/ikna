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

## Decision

```
capacity  = targetDailyReviews                 (returnModeCapacity while in return mode)
projected = max(dueToday, forecastAvg3d) + backlogWeight * backlog
headroom  = capacity - projected
allowedNew = clamp(headroom / costPerNew, 0, effectiveMaxNew)
```

`effectiveMaxNew` is `maxNewPerDay`, raised by the accelerator below once the
settling window has passed.

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

## Safety valve

If nothing was introduced in the last `safetyValveDays` days (7 by default — the window is
counted by the repository, not here), exactly one new chunk is released regardless of
every gate. Without this the governor can latch at zero forever, novelty disappears, and the
app becomes the treadmill it was built to avoid.

## Accelerator

After `accelerateAfterCleanDays` clean days with accuracy above 0.9, `maxNewPerDay` grows by
`accelerateStep`, capped at `maxNewCeiling`. Load rises to meet current form without being
asked.

## Amnesty

Overdue cards are not queued. They move to an amnesty pool and are drip-fed at
`amnestyQuotaRatio` (20%) of each session, while FSRS honestly recomputes their stability from
the elapsed time. The session counter is clamped to `capacity`, so the UI is structurally
incapable of displaying a four-digit number of pending cards.

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

## Serialised writes

Answering is a read-modify-write on one `daily_stats` row. Two fast swipes used to overlap and the
second write erased the first, so a card was answered but not counted — and that counter feeds the
measured norm, which feeds capacity, which decides how much new material the day gets. Writes that
touch card state, the review log and the day counter now go through a single mutex in
`LearningRepository`. Swiping fast is the normal speed of a good session, not misuse.
