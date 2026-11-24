# Remote journal synchronization: proposed boundary

Status: Round52 design, not an enabled remote synchronization feature. No provider,
account, credential, network adapter or deployment is selected by this document.
The existing foreground LAN implementation remains separate. The isolated code increment supplies a provider-neutral storage contract, a
test-only in-memory CAS backend, and a bounded Tink envelope codec. It has no
production callers. Manifest publication, durable rollback checkpoints, platform
key protection and device key sharing below are design requirements, not
implemented functionality. Local tests must not be described as PC-to-phone
internet acceptance.

## Decision before production implementation

The owner chooses storage they control. Recommend HTTPS WebDAV **only after** the
specific service passes strong-validator/conditional-write conformance tests.
An object store with equivalent atomic conditional writes is another suitable
adapter. No Ikna account is needed; a storage-provider account may be needed.
A consumer synced folder is not interchangeable with atomic remote storage:
conflict copies, delayed visibility and last-writer-wins replacement need another
protocol. Do not implement a shared mutable head on that assumption.

The second decision is how devices unlock and recover their encryption key.
Recommend a random shared keyset protected locally by Android Keystore and an
appropriate desktop OS credential facility, with explicit user-held recovery
material. Linux support needs its own selected key-store implementation or a
clearly explained unlock alternative. Do not silently fall back to plaintext
key storage. Losing every device and recovery material means losing access to
the encrypted remote copy. Local plaintext export remains separately governed.

Provider setup, credentials, real uploads, storage permissions, recovery-secret
transmission and actual security-setting changes require their own authorization.
This design does not authorize any of those actions.

## Storage interface

A minimal adapter supplies only:

- bounded `get(key, maximumBytes)` returning absent or complete bytes plus an
  opaque strong version token;
- atomic `create(key, bytes)` that fails if that key already exists;
- atomic `replace(key, expectedVersion, bytes)` that fails if the version changed.

It distinguishes absence, conflict, authentication failure, quota, unsupported
semantics, unavailable service and indeterminate write result. A network exception
is not proof that a write failed. No unconditional replacement, remote delete or
correctness dependence on listing is required. Storage bytes and version tokens
are opaque to the adapter; the encryption layer owns interpretation.

Real adapters must enforce TLS with normal hostname/certificate verification,
explicit timeouts, response-stream byte caps and a fixed configured origin/root.
Redirects must not forward credentials across origins. Provider error bodies,
credentials, recovery material and journal text must not enter logs.

HTTP strong ETags with `If-Match`, and creation with `If-None-Match: *`, provide
the intended conditional request semantics. A service calling itself WebDAV or
S3-compatible is not sufficient evidence. An adapter must test a stale write,
simultaneous contenders, stale/weak validator rejection and ambiguous responses.
ETags are concurrency tokens, never cryptographic authenticity proofs.

Primary references: [HTTP conditional requests](https://www.rfc-editor.org/rfc/rfc9110.html#name-conditional-requests),
[WebDAV](https://www.rfc-editor.org/rfc/rfc4918.html),
[an object-store example of conditional writes](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html).
The last reference establishes feasibility, not selection of AWS.

## Encryption and authenticated context

Use an established high-level AEAD implementation, proposed Tink
XChaCha20-Poly1305 on JVM/Android. Let the library generate and encode its random
192-bit nonce. Do not derive nonces from timestamps, event IDs, restored counters
or device IDs. Retries resend the same saved ciphertext; changed plaintext is a
new encryption. Use library key generation and keysets, never a human password
as a raw encryption key, copied cryptographic internals or a home-grown cipher.

AAD has one versioned, unambiguous length-prefixed binary encoding. It binds the
protocol/domain, group, history epoch, production/developer profile, object kind,
object identifier, writer identity, key generation and object revision. Those identifiers are
opaque, nonsecret values. Journal content and detailed learning metadata remain
inside ciphertext. Identity fields parsed from a header are untrusted until AEAD
verification and comparison against the expected local context succeed. Shared-key
authentication proves membership in the trusted group, not individual device
authorship: any member holding the group key can claim another writer identity.
Per-device signatures are not implemented or promised.

Tink supports XChaCha20-Poly1305 in Java. Its current setup page names 1.23.0,
Java 11+, and full Android support from API 24; this repository targets JDK 17
and Android API 29+. Before introducing a pinned dependency, verify the actual
artifact and both platform builds: documentation is not compilation evidence.
The isolated codec pins Tink 1.23.0 (`tink` on desktop, `tink-android` on Android);
key generation is
used only by synthetic tests. Executed build evidence belongs in the Round52
report. AES-GCM-SIV would add a Java provider requirement; default AES-GCM requires stricter nonce/message-limit reasoning.

Sources: [Tink AEAD](https://developers.google.com/tink/aead),
[supported key types](https://developers.google.com/tink/supported-key-types),
[Java/Android setup](https://developers.google.com/tink/setup/java).

## Minimal object publication protocol

An opaque group prefix contains an encrypted CAS head and immutable encrypted
journal objects plus immutable encrypted manifest history. The head identifies a
manifest by cryptographic digest. A manifest binds its sequence, predecessor
digest, epoch, key generation and writer roots; the roots identify exact bounded
journal objects. Every fetched ciphertext is authenticated before use. The
expected object identifier/digest comes from authenticated ancestry or local
trusted configuration, not an unauthenticated remote listing.

Initially retain the existing 4 MiB/50,000-record journal cap; larger journals
fail visibly without truncation. A concrete first protocol should also cap writers
(e.g. 8), manifest bytes (64 KiB), total fetched bytes (64 MiB), ancestry traversal
(128 objects), and conflict retries per foreground cycle (3). Exhaustion reports
a blocked/unfinished exchange; it is never silently treated as current. These
are proposed safety limits, not measured capacity or a pagination implementation.
No remote deletion or garbage collection in the initial version.

1. Keep local changes and an upload intent durable before remote publication.
2. Fetch and authenticate the current head/manifest and required roots. Check
   ancestry against the last trusted checkpoint and the current history boundary.
3. Merge through the existing stable-event-ID transaction/replay layer. Conflicting
   event IDs, unsupported schema or missing content fail closed, as for LAN sync.
4. Upload immutable encrypted objects and manifest first, using create-if-absent.
   A collision requires verification of the exact stored ciphertext, never overwrite.
5. CAS the head using its observed version. On conflict, authenticate the new head,
   retain both histories and pending local changes, then recompute a union and retry.
   Never retry an obsolete head over a new one without this merge.
6. If a write result is indeterminate, reread and compare exact authenticated
   publication identity before retrying. An orphan immutable object is acceptable;
   a head pointing to an incomplete publication is not.

Offline devices publish later. Their histories are not replaced by the newest
snapshot, and elapsed wall-clock time never decides which events survive. A
single CAS head deliberately serializes publication while event union handles
concurrent learning. A restored/cloned writer identity needs explicit recovery,
not two independent publishers continuing an old private counter.

## Rollback, replay, crash and trust limits

Persist an authenticated accepted head sequence **and digest**, group/epoch/key
binding, and the relevant ancestry checkpoint locally outside ordinary export.
Reject lower sequences, same-sequence different digests and any candidate whose
authenticated ancestry fails to descend from the trusted digest. Sequence alone
is insufficient. A disappeared previously trusted head is not an empty new group.

Before merging a received publication, durably record its authenticated digest as
a pending checkpoint. Merge idempotently in the repository transaction, then
commit the trusted checkpoint and clear pending state. Restart must complete or
safely reattempt that same merge before trusting another publication. Never
advance trust on unauthenticated metadata or before successful merge. Integrating
this crash boundary with existing reset/restore serialization needs dedicated tests.

AEAD detects modification, not freshness. A malicious store can withhold data,
return an older unseen branch to a new peer, deny service or fork isolated peers.
Trusted local checkpoints detect incompatible history already observed; they do
not prove that the store supplied the newest state. If all trusted checkpoints
are lost or rolled back together, there is no independent freshness oracle.
Recovery must state this limitation and require explicit trusted-device or
user-recovery reconciliation, never claim rollback-proof cloud storage.

Duplicate delivery is harmless only because the event union is idempotent; it
must not double-count reviews or undo. Existing reset/restore history boundaries
remain mandatory. A new epoch is explicit and old peers cannot revive its prior
history. Do not silently interpret a key change as a history reset.

## Pairing, recovery and revocation

A random group keyset is transferred only between explicitly trusted own devices,
for example by extending the confirmed pinned LAN pairing channel, or through an
explicit user-held recovery mechanism. Neither mechanism is implemented here.
The remote provider receives ciphertext, never plaintext keysets or recovery
secrets. Provider-managed KMS is not the proposed trust model.

Use Android Keystore to wrap the exportable sync keyset rather than trying to
share a non-exportable device key. Desktop wrapping needs a separately verified
platform implementation. OS key storage reduces offline key exposure; it cannot
protect a running compromised app. Ordinary app backup must not copy private
credentials, keysets or checkpoint identity into another writer silently.

A removed device still knows old keys. Effective revocation needs a new key
generation distributed only to remaining trusted devices, exclusion of old
writers, and revoked/replaced storage access. Previously downloaded ciphertext
cannot be made unreadable retroactively. Recovery re-enrollment and key rotation
need explicit handling of offline devices and pending events; no unattended
rotation or re-admission is implied.

Sources: [Tink key management](https://developers.google.com/tink/key-management-overview),
[Android Keystore](https://developer.android.com/privacy-and-security/keystore).

## Acceptance boundary

Local fake-store tests should cover atomic create/CAS, two offline writers,
conflicting publishers, retained pending events, duplicate delivery, ambiguous
success, interrupted publication, tampering, wrong AAD/profile/epoch/key, bounds,
missing objects, rollback/forks and crash recovery. A fake store alone cannot
establish TLS, provider semantics, account permissions, OS-backed key protection,
real distant-device connectivity, Android background operation or end-to-end
sync completion. No real provider is enabled until those relevant gates pass.
