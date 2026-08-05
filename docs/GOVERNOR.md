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
| `reviewsDoneToday` | used for the post-skip warm-up gate |
| `cleanDays` | consecutive days where the day plan was completed |
| `newIntroducedLast7d` | feeds the safety valve |

## Decision

```
capacity  = targetDailyReviews                 (returnModeCapacity while in return mode)
projected = max(dueToday, forecastAvg3d) + backlogWeight * backlog
headroom  = capacity - projected
allowedNew = clamp(headroom / costPerNew, 0, maxNewPerDay)
```

`costPerNew` defaults to 4.0. This is the part every other app gets wrong: one new chunk is not
one card today, it is roughly four reviews over the following week. Treating new material as
free is what produces the avalanche three weeks in.

## Hard gates (force allowedNew = 0)

| Gate | Condition |
| --- | --- |
| `BACKLOG_LIMIT` | `backlog > backlogHardLimit` |
| `POST_SKIP_WARMUP` | skipped a day and `reviewsDoneToday < warmupReviewsAfterSkip` |
| `LOW_ACCURACY` | `accuracyRecent < minAccuracy` |
| `RETURN_MODE` | gap of 14+ days, for `returnModeDays` days |

The post-skip gate is the behavioural core: after a missed day, new material is *earned* by
attention, not handed out to help you "catch up". Gamified apps do the opposite.

## Safety valve

If nothing was introduced in the last 7 days, exactly one new chunk is released regardless of
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
