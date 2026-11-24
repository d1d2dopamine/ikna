# Incremental local-first journal synchronization

Incremental implementation and executed acceptance are tracked in [Round54](ROUND-0.12-54.md).
Round56 adds purpose-aware outer wire envelope 4 while retaining exact version-3
consent/journal bindings and existing stream prefixes; see
[optional review compatibility](OPTIONAL-HARD-REVIEW-0.12.md).
This design document is not itself test evidence.

## Scope and consent

Each device keeps its own installed content and learning history. The existing
foreground, paired, pinned-TLS connection remains the transport. Both applications
must be open and reachable; there is no new internet relay, cloud account or
background delivery guarantee.

Version 3 retains the exact two-sided profile preview/approval before initial
mutation. A saved version 2 completion does not silently authorize the new exchange:
both devices need compatible applications and a fresh approved preview. Choosing
one device's history still uses the separate verified database/recovery workflow.
Settings and media remain device-local.

## Durable event identity

The approved bootstrap resolves the existing active legacy journal once using the
same portable identity normalization. In the consent receipt transaction it records
local-row to canonical-event/digest/Undo mappings and creates a fresh stream.
History rows are not rewritten. New committed events are indexed in bounded batches
under a fixed observed row high-water mark; known unindexed rows prevent a
caught-up indication. Unknown later legacy rows, changed raw identities or ambiguous
duplicates fail closed rather than being renumbered.

Schema13 adds three derived synchronization tables. Migration 12→13 creates these
empty tables without generating consent or changing existing history tables.
Known schema 12 adopted/recovery slots retain origin/integrity checks and use the
explicit Room migration; an unpublished candidate must have the current schema.
Unknown versions are rejected. Wiping all app data also clears this derived state.

## Exchange and commit

A device advertises bounded inventories containing portable IDs and canonical
payload digests. The other device asks for missing payloads; an identical known
event does not need its text fields retransmitted. Undo dependencies are checked
against active history and included where necessary. One page per direction is
outstanding. Each page carries its fixed observed high-water proof; after its
acknowledgement the next page may use a newer observed head. An entire multi-page
exchange is not a frozen global snapshot. Inventory/data and decoded-size limits are enforced; an oversized
individual event/dependency is a visible error, never a skipped event. Storage
budgeting and both incremental wire layers use matching null-field encoding; the
frame limit is checked on the actual encoded frame. Already-known records also
count toward the complete page budget, not only missing payloads on the wire.

The receiver validates the complete page, content identity and current history
boundary, then adds records, updates derived learning state and advances its
contiguous source checkpoint in one Room transaction under the learning write
guard. Acknowledgement follows commit. Duplicate/reordered/retried traffic cannot
advance across a missing prefix or replace a conflicting event. Checkpoints bind
the authenticated devices, stream, consent, group/generation, content and boundary.
A new source stream cannot silently replace the saved one under the same consent.

Reset, restore, adoption and recovery invalidate prior stream/checkpoint authority.
A saved checkpoint that cannot be verified stops exchange; no automatic index
renumbering is attempted. Re-pairing and fresh profile review may be required.

## Meaning of status

Bilateral consent completion is distinct from journal catch-up. Successful control
messages alone do not mean all observed events were exchanged. Catch-up means both
directions reached their latest observed high-water marks with no pending page or
known unindexed tail. Later edits can make another exchange necessary. The last
successful timestamp is historical, not proof that an offline peer has current data.
An actionable oversized-event or reconciliation error remains visible across
successful control-only messages for that peer/consent. Catch-up, disabling or
replacing the connection/consent clears that retained error; transient network
errors do not overwrite it permanently.

## Explicit limits

- Initial profile preview/approval still has the existing 4 MiB encoded and 50,000
  rows-per-table limits. Content changes, lost checkpoint recovery or re-pairing
  can require another initial preview; incremental history growth does not remove
  that gate. Do not delete history just to fit it.
- Post-consent content fingerprinting does not load/count the whole journal.
  Content itself remains bounded. Network pages are bounded, but deterministic
  scheduling replay after genuine additions still reads history. This is not a
  constant-memory or constant-time learning engine.
- Event inventories are metadata, not a claim of zero network traffic for known
  history. Imported events may appear once in the reverse inventory; matching
  identities suppress duplicate payloads and learning effects.
- Timestamp ordering still has the previously documented clock-causality limits.
  Arbitrarily wrong device clocks are not repaired by pagination.
- Local JVM/socket tests do not prove physical Android/Windows lifecycle,
  power-loss durability, mobile background behavior or internet reachability.

## Upgrade and rollback

Room migration is explicit and preserves existing history; no destructive fallback
is allowed. An older application must not be pointed at a newer-schema
file (Round54: schema13; Round56: schema14). Retaining an old source ZIP is not a database backup. Before deploying an
upgrade to real learner data, retain a verified pre-upgrade backup/export and
custom-content recovery material. Downgrade requires a compatible preserved
backup, not manually changing user_version or deleting synchronization tables.
The profile-adoption recovery database is a separate feature and does not promise
arbitrary cross-version rollback.
