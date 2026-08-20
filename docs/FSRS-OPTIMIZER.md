# The local optimiser

The scheduler ships with `FsrsParams.DEFAULT_W`: twenty-one weights fitted across
many people, many collections and many subjects. They are a good prior and they
are nobody's memory in particular. The `reviews` table already holds what is
needed to do better for one person -- a timestamp and a grade per answer, never
deleted -- so the fitting happens on the phone, and the log stays where
`PRIVACY.md` says it stays.

`FsrsOptimizer` is that fitting. **It is an estimator and nothing else.** Nothing
reads its output yet: it is not wired into settings, not scheduled, and not
applied to any card. That is on purpose -- the estimate had to be shown to be
worth trusting before anything was allowed to depend on it, and the section on
parameter recovery below is the reason that order matters.

## How it works

1. Answers are grouped into one history per card key (`chunkId:level`, because
   levels are separate memories).
2. Each history is replayed through the shipping `Fsrs` object. Not a private
   copy of the formulas: a copy would drift from the scheduler, and both halves
   would go on producing plausible numbers while disagreeing.
3. Each replayed answer with at least a day since the previous one is scored by
   binary log loss against the predicted retrievability. Same-session repeats are
   replayed but not scored: retrievability there is about 1 by construction, so
   scoring them measures how often a session re-shows a failed card.
4. The most recent 30% of scored answers are **held out**. Fitting sees only the
   earlier 70%.
5. Coordinate descent from the defaults: each weight is nudged up and down within
   its bounds, the move is kept if the objective improves, the step halves, six
   passes. Deterministic, no learning rate, no way to diverge.
6. The objective is training loss plus a pull towards the defaults, measured as a
   fraction of each weight's own range so that days and exponents are comparable.
7. The fit is returned **only** if it predicts the held-out weeks better than the
   defaults do by more than `MIN_IMPROVEMENT`. Otherwise the verdict is
   `NO_IMPROVEMENT` and `params` is null.

Below `MIN_SCORED_ANSWERS` scored answers, none of this runs and the verdict is
`TOO_FEW_ANSWERS`.

## What the constants are, and why

| Constant | Value | Why this number |
| --- | --- | --- |
| `MIN_SCORED_ANSWERS` | 500 | A prototype log of about 640 scored answers was rejected by the held-out gate at every regularisation strength tried. Below this, fitting is fitting to noise. |
| `HELD_OUT_SHARE` | 0.3 | Enough recent answers to notice a fit that only explains the past. Split by answer count, not by calendar, so a fortnight away from the app does not become the whole test set. |
| `MIN_IMPROVEMENT` | 0.001 nats | Smaller than this is not a difference anyone experiences. |
| `REGULARISATION` | 0.1 | Chosen from the sweep below: it cut the distance from the true parameters by more than half at a cost of about 0.0009 nats of held-out loss. |
| `MAX_ANSWERS` | 20 000 | Newest answers first. A ten-year log should not turn a background job into a battery complaint. |
| `MAX_PASSES` | 6 | 504 loss evaluations. About 1.8 s for 2 900 answers on the prototype's hardware. |

## What was measured

Synthetic logs, generated with the shipping model, so the true weights are known.
The honest results, not the flattering ones:

**300 cards, 2 925 answers (1 519 train / 784 scored held-out):**

| | train loss | held-out loss |
| --- | --- | --- |
| defaults | 0.33359 | 0.32687 |
| fitted | 0.32731 | **0.32310** |
| true parameters | 0.33176 | 0.32678 |

Two things in that table are worth staring at. The fit beats the defaults on
weeks it never saw, which is the point. And it also beats *the parameters the data
was generated with*, which is the giveaway: it is absorbing noise as well as
signal, and the only reason that is acceptable here is that the gate measures the
result on data the fit never touched.

**Regularisation sweep, same log:**

| lambda | held-out loss | distance from true weights |
| --- | --- | --- |
| 0 | 0.32389 | 2.660 |
| 0.02 | 0.32408 | 1.989 |
| **0.1** | **0.32478** | **1.212** |
| 0.5 | 0.32528 | 1.166 |

**Log size, at lambda = 0.1:**

| scored answers | verdict |
| --- | --- |
| about 640 | rejected |
| about 2 300 | accepted, 0.3269 -> 0.3231 |
| about 7 300 | rejected |

The third row is not a typo, and it is the most useful row in this document. More
data does not guarantee acceptance. A longer log covers a longer stretch of a
changing person, and the defaults are hard to beat.

## Parameter recovery: the uncomfortable part

Distance from the true weights went from 1.19 to 3.60 when fitting was run
without regularisation. **Prediction improved while the parameters got further
from the truth.** Several weights sat on their bounds at the end of the search
(indices 7, 19 and 20 consistently; 0, 9, 12 and 16 on the small log), which is
what a flat loss surface looks like from the inside.

So the claim this file is willing to make is narrow: *these weights predict this
person's recent recall better than the defaults did.* Not: *these are this
person's memory constants.* Anything in the interface that ever exposes this must
say the first thing. "Your personal memory parameters" would be a lie the numbers
do not support.

## Deliberately not estimated

`desiredRetention`. It is the trade between daily work and how much is forgotten,
which is a decision, not a measurement -- and the load governor already owns how
much work a day is allowed to be. See `docs/GOVERNOR.md`.

## If it is ever wired in

In roughly this order, and no further than the user has asked to go:

1. Run it in a worker, never on the answer path. Seconds of arithmetic.
2. Store the fit next to the log, not in place of the defaults, and keep the
   verdict and both losses with it. A number whose provenance was thrown away
   cannot be reviewed later.
3. Make it switchable off, and make switching it off restore the defaults
   immediately, without touching a single scheduled card's history.
4. Say what it did in plain language, with the refusal cases spelled out: "not
   enough history yet" and "your own history did not beat the defaults" are both
   normal outcomes, and the second one is not a failure of the user.
5. Re-fit rarely -- monthly is plenty -- because a schedule that quietly changes
   every day is a schedule nobody can plan around.
