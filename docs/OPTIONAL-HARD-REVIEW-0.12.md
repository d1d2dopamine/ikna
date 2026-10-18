# Optional hard-card review

Round 56 implements the owner's 2026-10-17 decision: finishing the main lesson
must remain a finish, with a separate voluntary way to revisit difficult cards.
Implementation and verification status is recorded in [ROUND-0.12-56.md](ROUND-0.12-56.md).

## Contract

- Availability reads the existing global required plan. Completing only a deck's
  filtered portion, or reaching a raw answer count, is insufficient. Showing the
  offer does not create a plan or introduce cards.
- Each explicitly requested batch contains at most five already learned level-0
  targets with at least four recorded lapses, the existing hard-card criterion.
  Scope membership and suppression are checked before the limit. Membership in
  multiple decks does not create multiple learner memories.
- The batch is separate from the required daily plan. It has one pass, including
  after an Again answer, and can be stopped without losing main-lesson credit.
  Starting another batch always requires another explicit action.
- These are real graded answers. Existing grading and FSRS update the schedule;
  factual review/time/accuracy and existing load estimates include them. This is
  not an ungraded preview or a claim that extra practice is always beneficial.
- Optional answers do not satisfy required-plan keys or earn new-material credit,
  immediately or through a later ordinary answer. The persisted purpose survives
  Undo, supported backup restore, replay, profile adoption and synchronization.
- A batch is ephemeral. Day changes, reset/history-boundary changes, removed or
  suppressed targets and invalidated tokens reject a stale answer. Restarting
  does not create an obligation to finish an abandoned batch.

## Compatibility

Room schema 14 adds nullable review purpose. Null is the existing normal-answer
meaning; historical rows are not rewritten. Null is omitted from portable event
JSON, preserving historical IDs, canonical payloads and incremental prefixes.
Unknown non-null purposes fail closed in current readers.

The transport envelope is version 4. Older envelope versions are rejected before
nested payload interpretation. The unchanged durable consent and journal-binding
version remains 3; this separation avoids forcing unchanged large histories back
through the initial profile-size limit. All paired apps must support envelope 4.

Manual JSONL backups containing optional answers require this or a newer
purpose-aware app. Older apps ignored unknown JSON fields and could interpret
such answers as normal. Do not import these backups into older versions or
attempt to open schema-14 databases with older binaries. Keep the pre-upgrade
backup for deliberate rollback; that backup does not contain later answers.

## Presentation

The completion screen offers the existing quiet secondary action for optional
hard-card review. Its queue has its own progress and a Finish action. There is no
new navigation tab, automatic extra lesson, penalty, streak requirement or
changed card content. The existing “A bit more” route retains its ordinary lesson
policy; its caption is corrected to avoid promising that later ordinary answers
can never earn new cards.

## Evidence boundary

Repository, serialization, protocol and migration tests are distinct from real
phone/Windows interaction checks. No physical-device accessibility, touch,
background or remote-internet behavior is implied by JVM tests. Existing sync
limits remain: foreground reachable peers, bounded initial profile transfer,
whole-history replay cost, and no universal server-free remote connectivity.
