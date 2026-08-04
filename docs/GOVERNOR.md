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
