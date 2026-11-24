# Round54 — incremental local-first journal exchange

Status: completed local implementation and verification stage, 2026-10-17.
Independent-process interruption and physical-device acceptance remain open.

Scope: bounded inventories and missing-event batches after explicit version-3
bilateral consent; durable identity/index/checkpoint metadata with schema 13;
content-only post-consent validation; retry/restart and generation safety. Full
local copies are retained. Initial full-profile approval remains capped;
whole-history scheduling replay remains a separate resource cost.

Executed focused run 5: 71 tests passed, zero failures/errors/skips: incremental
protocol 12, profile consent 24, actionable-error retention 3, real SQLite
repository 14, real controller/Room/TLS integration 18. The 1,117-event history
beyond the initial profile byte cap converged after controller/database restart
in 16.073 seconds, within the unchanged 60-second test budget. Same-process Linux
loopback is not physical Android/Windows evidence.

Earlier runs are retained outside the source package: a test-helper compile
failure, synchronized SQL-recorder iteration failures, and a genuine large-page
wire encoding mismatch. The latter was reproduced before the fix: 256,678-byte
repository page became a 280,642-byte explicit-null frame, exceeding 262,144;
matching canonical encoding produces 257,172 bytes. Both wire layers now use
matching null encoding; limits and semantic assertions were not weakened.

Final frozen-source verification: 1,134 passes, two unchanged pointer-test skips,
zero failures/errors across 1,136 cases. Shared desktop 140; desktop 297 including
two skips; shared Android local-JVM 133; app Android local-JVM 566. All 452 captured
build inputs remained unchanged. Standard debug APK and desktop distributable
builds passed; unchanged APK signing certificate verified. Linux packaged
--selftest passed with zero decks/cards, schema 13, quick_check=ok and all three
new derived tables empty. Corpus-free smoke does not test starter-deck import.

Static checks: UTF-8/NFC text, seven locales with 852 keys each, Android identifiers,
43 design-contract cases and five classic-card SQL cases passed. The wipe checker
now executes against committed schema 13 and verifies all 17 mutable tables plus
preserved origin. See docs/evidence/sync-round54/ for source/XML/build/log hashes.

See SYNC-INCREMENTAL-0.12.md for scope, generation/re-pair limitations and
migration/rollback precautions. These tests run in Linux/JVM; two controllers in
one JVM do not establish independent-process crash, power-loss, physical-device
or internet behavior. The next bounded stage explicitly targets process crashes.
