# Grading

**Status: decided, not implemented.** This file records a design decision taken
during the `proof` epoch so it does not have to be re-argued. Nothing described
under "Derived grades" ships in `0.1.0 proof`. What ships today is the binary
swipe described in the first section.

## What ships today

One axis. Left is `AGAIN`, right is `GOOD`. Vertical movement springs the card
back and grades nothing. See `ui/session/SwipeDecision.kt`.

FSRS-6 accepts four grades and the `Rating` enum already carries all four.
`HARD` and `GOOD` differ by `w[15]`, `EASY` by `w[16]`. Two of the four are
currently unreachable from the UI: the scheduler can use them, nothing produces
them.

## Why there are not four directions

There were, and they were removed. Reconstructing the reasoning so it is not
rebuilt by accident:

1. **Four directions ask a second question.** The first question is "did you
   know it". The second is "how well did that go". The second one is a
   self-assessment made in under a second, and it is the exact category of
   micro-decision this app exists to delete.
2. **The vertical axis collides with the hand.** A phone is held low. A thumb
   travelling right also travels up. The axis was chosen by whichever
   displacement was larger, so "I knew it" regularly landed as "that was easy" —
   silently, and with a different schedule attached.

The second reason is the fatal one. It is not a tuning problem; it is anatomy.
Any future proposal that puts a grade on the vertical axis has to answer it.

## The cost of two grades

One answer carries one bit: known or not known. On a card seen many times the
answer is almost always "known", so most answers carry close to nothing. FSRS
still schedules correctly, but with less resolution than it is capable of.

## Derived grades

The grade is **measured, not asked for**. The gesture stays exactly as it is; two
observations about how the gesture was made supply the rest.

| Signal | Source | Meaning |
| --- | --- | --- |
| Latency | card shown -> drag start | how long retrieval took |
| Peek | `PEEK_TRAVEL` already tracked in `SwipeDecision.kt` | the back was consulted before answering |

Mapping:

| Gesture | Grade |
| --- | --- |
| left | `AGAIN` |
| right, peeked or slow | `HARD` |
| right, ordinary | `GOOD` |
| right, fast and no peek | `EASY` |

No new buttons, no new decisions, no UI change, and four real FSRS grades
instead of two. Roughly two bits per answer instead of one.

Peek is the stronger of the two signals. It is nearly binary and it is
deliberate: a person who turned the card over and then swiped right did not
know it cleanly. Latency is the weaker, noisier signal and is treated as such.

## Calibration is per person, on device

There is no shared model and no population baseline. "Fast" and "slow" are
defined only against the same person's own recent answers.

- A rolling window of the last ~200 timed answers, held on the device.
- Thresholds are percentiles of that window, not constants. Roughly: below the
  25th is fast, above the 75th is slow.
- Normalised by presentation level and by chunk length. Reading a long carrier
  sentence is not the same as struggling with it.

The window is a ring buffer. Answer 201 evicts answer 1. It never resets, so the
thresholds track a person who is getting faster at the language, or who is
tired, instead of averaging over a person who no longer exists.

This is why the feature needs **no server, no upload, no accounts, and no other
users.** The app opens exactly one connection, to ask whether a newer release
exists, and no answer, rating or timing may ever travel on it. Any design that
needs to collect answers centrally is the wrong design, not a missing feature.

## Dirty data

People get distracted. The window will contain garbage. Four layers handle it,
in order of importance.

**1. Percentiles, never averages.** One answer of forty minutes destroys a mean
and moves a median by one position out of two hundred. Breaking a percentile
requires corrupting roughly half the window, and distraction does not happen
half the time. Robustness to outliers is the reason the thresholds are
percentiles in the first place.

**2. Reject the obviously broken before it enters the window.** The device
already knows about most of it:

- the app lost focus, or the screen turned off, during the card;
- the answer took longer than a hard ceiling (60s, or 5x that person's median).

In those cases the **timing** is discarded, not the answer. The review is logged
and scheduled exactly as before; only the stopwatch is dropped.

**3. Unknown falls back to `GOOD`.** A card with no usable timing is graded the
way the app grades everything today. The absence of a signal is never read as a
signal.

**4. Noise is one-directional.** Distraction makes an answer slower. It cannot
make one faster. So noise can produce a spurious `HARD`, never a spurious
`EASY`. `HARD` shortens the interval, meaning the card comes back sooner — the
harmless direction. The dangerous failure, "hidden for months because you looked
confident", is structurally impossible.

## Safety rules

These are constraints on the implementation, not suggestions.

1. **Never worse than today.** When in doubt, grade `GOOD`. The current binary
   behaviour is the floor, and the derived grade only refines cases where the
   signal is clear.
2. **Bounded effect.** A derived `HARD` or `EASY` may move the interval by at
   most about 30% against what `GOOD` would have given. A wrong grade costs
   nine days instead of seven, not six months instead of one.
3. **Warm-up.** Below ~50 timed answers there is no window worth trusting.
   Everything answered right is `GOOD` until there is.
4. **Off by default** until the validation below says otherwise.
5. **Record the raw signals in `reviews` regardless.** Latency, swipe velocity,
   peek flag, and a discard reason. Storing them is additive and cheap; not
   storing them means starting from zero later.

## How to tell whether it works

The review log is append-only and the derived tables rebuild from it, so this is
cheap and needs exactly one person's data.

1. Replay a real log twice: once grading binary, once grading derived.
2. FSRS produces a predicted recall probability for every review. The log holds
   what actually happened.
3. Compare predictions against outcomes in both runs.

Lower error means it works and can be switched on. No improvement means the code
is deleted, and nothing is lost, because the raw signals stay in the log and the
history was never rewritten.

This is a scheduler change, so it must also pass the log-replay round-trip test
before it goes anywhere near a release.

## Known caveat

The FSRS-6 default parameters were fitted on Anki data, where `HARD` and
`EASY` come from a person pressing a button about themselves. Derived grades do
not mean quite the same thing, so `w[15]` and `w[16]` are not guaranteed to fit
them. Keep the derived grades conservative until per-user parameter optimisation
exists, at which point the mismatch resolves itself.

## Order of work

1. Record latency, velocity, peek and discard reason in `reviews`. Behaviour
   unchanged, nothing visible to the user. Small.
2. Accumulate real logs. Weeks, not a sprint.
3. Run the comparison above.
4. Only then, if the numbers agree, implement grading and ship it off by
   default.

Step 1 is the only part that belongs to the `proof` epoch. Steps 3 and 4 are not
scheduled and do not have to be.
