# Round 44: statistics about actual study material

2026-10-12. Owner explicitly requested different statistics content, finding the
previous screen uninformative and poorly suited to the intended audience. This
is a content change within the existing visual system, cumulative on Round 43.
No Catalogue v2/discovery or cosmetic exploration is included.

## Visible content

The main view has a 7/30-day selector and three factual counts:

1. **Cards answered:** distinct stored global target IDs with retained answers
   in the selected period. Different deck memberships do not duplicate an ID;
   identical spellings with different IDs/languages are not merged by text.
2. **First recorded:** targets whose earliest retained, non-retracted answer is
   inside the period. This is not a first encounter, acquisition date or learned
   vocabulary count. Imports/restores may supply an earlier answer; incomplete
   or cleared history limits what can be known.
3. **Review answers:** retained answers with `prevIsNew == false`. First contacts
   are excluded. Missing snapshots remain in the all-answer/target totals but
   cannot supply repeat-answer or recall evidence.

The total number of answers is also shown. Details explain the calculations,
known-repeat recalled/total counts, unknown snapshots, source-history limitations
and exact date/time bounds. There is no language-knowledge percentage, streak,
attendance grid, best-hour recommendation or future obligation forecast in the
new primary screen. Existing legacy statistics APIs are retained for source
compatibility, but these platforms use the new shared renderer/repository.

Two compact sections offer concrete cards on request:

- **After a difficult answer:** in this period, a known repeat miss is followed
  by at least two known repeat recalls after the latest retained miss. Unknown
  or first-contact successes cannot supply evidence; any retained miss interrupts
  the sequence. This describes recorded later answers, not permanent learning.
- **Repeated difficulties:** at least two known repeat misses in the period and
  the latest retained answer to that question is a known repeat miss.

Patterns use the latest answered question level for each target and do not join
old question types into an invented sequence. Each target appears once. Details
show up to five most-recent examples, with an explicit shown/total label if more
exist. Missing/deleted content remains counted from history and is labeled as
unavailable rather than silently disappearing. No new scheduling action follows
from either section.

## Measurement boundaries

- Windows contain the current study day plus the preceding 6/29 local calendar
  days, ending at the snapshot time. Their start uses the configured local day
  hour (currently 04:00); calendar arithmetic handles DST. Historical timestamps
  are viewed in the device's current timezone, displayed in details.
- Future answers and non-answer ratings are excluded. The DAO uses the existing
  append-only retraction predicate, so Undo removes an answer from statistics
  without rewriting its record.
- A single read-only Room transaction supplies each screen snapshot. Chunk reads
  are batched below SQLite bind limits. Loading does not write preferences,
  observations, schedules, plans or history. No remote collection was added.
- No session-duration statistic is presented. Recorded per-answer durations are
  not a trustworthy measurement of uninterrupted study-session time.
- Period counts may decrease when older answers leave the window. This is stated
  in the optional explanation rather than presented as lost ability.

The three main counts stay small; explanations and card details are optional.
This is not a claim that every neurodivergent learner wants identical statistics.

## Failure behavior

Both platforms now distinguish loading, successful empty history and a failed
read. A failure shows a persistent retry explanation, never fabricated zero
history. Cancellation is rethrown, and a completed read checks the originating
caller context before publishing its snapshot. Seven locale maps include the
new labels and explanations; existing typography, palette and controls remain.

## Verification

Evidence: [statistics-round44](evidence/statistics-round44/).

Shared tests cover identity deduplication, unknown/first-contact separation,
minimum recovery/attention sequences, distinct question levels, unknown miss
barriers, timestamp bounds, deterministic ties and local 04:00/DST windows.
Actual disposable Room tests cover undo-aware first-recorded counts, future
exclusion, unknown snapshots, missing content and non-mutating reads. An
independent source review found the mixed imported unknown-miss barrier issue;
it was corrected and a regression added before final verification.

Final compilation and all 69 shared plus 142 desktop tests pass, with zero
failures/errors/skips. The Linux release package and 11 isolated packaged-profile
processes also pass; results are recorded in the evidence. Android compilation and real Windows/Android layout,
large-text, focus and screen-reader acceptance remain open. Source guards are not
visual tests. No Windows resize/minimize fix is claimed.

The cumulative full ZIP preserves every Round 42/43 source, document and evidence
file, README, schemas, signing identity and the existing corpus/sample omissions.
