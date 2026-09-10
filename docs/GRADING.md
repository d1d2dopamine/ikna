# Grading

Automatic derived grading is active after a private on-device warm-up. It keeps
the interface binary: left means **do not know**, right means **know**. The person
never chooses AGAIN/HARD/GOOD/EASY and there are no number-grade shortcuts.

## Current card contract

The answer must be revealed before every final swipe or keyboard answer. This did
not change. There is no reveal/no-reveal branch and no additional micro-decision.
A pull on the front may reveal and return; only a later deliberate answer gesture
can grade the card.

The current timing protocol is `required_reveal_verified_v2`:

- the stopwatch starts when the card is actually laid out;
- it ends when the **final answer action** begins, after the answer was shown;
- reveal and answer must use the same modality;
- mouse/touch and keyboard build separate calibration windows;
- the answer must remain visible for at least 250 ms before timing is usable;
- animation, disk work and a fabricated keyboard velocity never enter latency.

A changed protocol string starts a clean cohort, so old first-gesture timings are
not mixed with the new verified-answer timings.

## Automatic mapping

| Binary input | Evidence | Internal FSRS grade |
| --- | --- | --- |
| left | any | `AGAIN` |
| right | unusable/insufficient timing | `GOOD` |
| right | slower than the personal 75th percentile | `HARD` |
| right | ordinary verified response | `GOOD` |
| right | faster than the personal 10th percentile and mature | `EASY` |

`EASY` now means an unusually fast **complete verified response**, not "the back
was never viewed". Required reveal is normal verification and is not punished as
peeking. To reduce impulsive false positives, a card needs at least three prior
successful reviews (`reps - lapses >= 3`) before a fast response can become
`EASY`. A new or still-fragile card stays `GOOD` even when fast.

## Calibration

Calibration is local and personal. No shared model or population baseline exists.
Each input modality reconstructs a ring of at most 200 recent, undo-aware,
current-protocol known answers. The current answer is classified before it can
enter the ring.

Refinement requires:

- at least 50 usable known answers in that modality;
- at least 20 usable answers at the current presentation level;
- valid 1–60,000 ms latency and 1–4,000 code-point prompt length;
- no focus loss, backgrounding, screen-off, mixed input, timeout or invalid clock;
- a real pointer velocity for pointer answers and no invented velocity for keys.

Latency is divided by the square root of prompt length, with levels calibrated
separately. Velocity proves that a pointer answer was observed but does not choose
the grade.

## Safety

Unknown evidence always falls back to the original binary grade. A derived HARD
or EASY uses the existing version-1 bounded schedule transform: stability and the
actual due delay can move at most 30% from GOOD. HARD never lengthens; EASY never
shortens. Original binary input still drives governor accuracy, daily statistics,
component learning and undo accounting.

Fast completion is useful evidence, not certainty. Someone can still reveal
impulsively and recognise the answer. The maturity gate, fastest-10% threshold,
250 ms verification floor and 30% scheduling cap limit that risk. Real local logs
over time remain the effectiveness test; synthetic fixtures validate plumbing,
not learning benefit.

## Data and replay

Raw observations and immutable decision context remain in the append-only review
log. Undo removes retracted rows from calibration. Restore replays the stored
rating and bounded scheduling version; it never reclassifies history using today's
thresholds. The protocol change needs no database migration because
`peekSemantics` already stores the timing-cohort identifier.

See [GRADING-IMPLEMENTATION.md](GRADING-IMPLEMENTATION.md) for CI and hand-off
commands.
