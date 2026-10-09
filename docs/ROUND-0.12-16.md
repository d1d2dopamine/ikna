# Round 16 — Everyday storage measurement and reader boundary

2026-10-08. Continue Part 12 from the accepted round-15 selected snapshot, using
`ikna-0.12-press-round-15-everyday-snapshot.zip` as the source baseline. No new
corpus, selection policy, target identity, cap, application behavior, schema,
version, workflow dispatch or publication is introduced.

## What changes

Add `tools/catalog/selected_storage_experiment.py` for the **selected intermediate**
input contract. The existing `storage_experiment.py` requires a built v2
`index.json` and PackChunk files; passing it selected rows would measure a
different or invalid shape. Leave that built-pack experiment unchanged.

The new offline command verifies the accepted passport and exact compressed and
logical input pins, stages rows in a disposable SQLite index, and writes three
lossless exploratory layouts:

- self-contained: one compact deterministic gzip per included deck;
- pair: learned-language context and meaning payloads shared only within a
  directed learning/meaning pair;
- language: pools shared across all meaning languages for a learning language,
  retained only as a diagnostic dependency comparison.

All root/context fields, including unknown fields, stay in the representation.
Every occurrence retains its complete `candidateId`/`origins` inline beside its
core/meaning references. A source sentence ID is not used to collapse or detach
different meaning references, versions or contributor names. Exact canonical
payload bytes determine reuse; no lossy text-only matching is performed.

The first context remains first and all alternatives retain their original order.
There is no generated deck-local ID to remove: these are selected memberships,
and even an unknown future ID field must survive. Gzip level 9 and compact JSON
change physical bytes and whitespace only; the retained source used gzip level 6.

## Full local result and decision

The final local run exits successfully. Exact report bytes are retained in
[STORAGE.json](evidence/everyday-round16/STORAGE.json) and the readable
[STORAGE.md](evidence/everyday-round16/STORAGE.md). Each layout verifies all
928,162 memberships, 2,094,192 contexts and 2,094,728 origin occurrences, across
328 included decks. Pooling covers 110 directed pairs or 11 learning languages;
both reproduce the full per-deck census. No source sentence, contributor field,
target identity or alternative is removed.

| Layout | Total bytes | Total MiB | Largest cold transfer MiB | Largest cold logical dependency MiB |
| --- | ---: | ---: | ---: | ---: |
| Self-contained | 248,934,879 | 237.403 | 3.005 | 15.906 |
| Pair pools | 203,526,226 | 194.098 | 3.540 | 19.142 |
| Language pools | 191,764,794 | 182.881 | 15.472 | 66.725 |

Self-contained totals include 248,805,336 asset bytes and a 129,543-byte
experimental layout descriptor. Baseline cold transfer counts the deck asset;
pooled cold transfer includes both shared files, its group descriptor and that
deck's references. The future common app catalogue/index is not measured here.
Cold logical dependency is the sum of decompressed dependency files and refs,
not RAM, installed DB size or reconstructed deck bytes. The compact baseline
contains 1,427,531,650 logical bytes; its median compressed deck is 552,109 bytes.
Original selected gzip size is 255,938,165 bytes (244.082 MiB), not a self-contained
deck collection or the common comparison baseline.

Pair pooling saves **18.241%** versus the self-contained intermediate, passes the
exploratory 220 MiB total/24 MiB cold transfer screen, and needs at most three
levels' shared evidence. Keep it as a candidate for final **pack** measurement,
with the self-contained final pack as control. This does not yet select a public
pool format or authorize a reader migration. The 19.142 MiB cold logical result
is already fairly close to the current 24 MiB ceiling before final token/credit
fields; their effect must be measured rather than assumed to fit.

Language pooling saves 22.966% overall but adds little over pair pooling while
making a cold deck depend on up to 30 decks' data: up to 15.472 MiB transfer and
66.725 MiB logical dependency. Reject it as the simple client candidate in this
round; retain the result as diagnostic evidence only.

[READER-BOUNDARY.json](evidence/everyday-round16/READER-BOUNDARY.json) binds the
current Kotlin models, importer, source-credit parser, Room schema, byte caps
and offline token/rank/build contracts by SHA-256. Required model fields, actual
schema columns, caps and full selected missing-field counts are cross-checked
locally. This is source-contract evidence, not executed app installation.

## Verification mechanism

Each layout is reread from actual files. Self-contained deck logical digests are
compared with per-deck digests taken while scanning the pinned original input.
Pool hashes and references are then read from disk, every original row is
reconstructed, and every field, ordered context and deck digest is compared.
Deck membership/context/origin counts must reconcile before a successful report
is written. Gzip CRC/truncation errors fail the command; partial files do not
constitute a successful measurement.

Output must be a new directory separate from the selected file and passport;
the tool never deletes/replaces a previous result or input. The SQLite index
preserves original within-deck order while limiting baseline gzip writers to one
deck. Writers use unnamed scratch spools and atomically publish only closed gzip
streams. The temporary stage is not a transport asset or source-ZIP member.

Two initial local attempts were rejected when rereading generated gzip files;
neither emitted a successful STORAGE report. Input compressed/logical identities
still matched, and a separate exact one-deck replay restored all 8,000 rows.
Sequential writing alone did not eliminate the observed truncation. Atomic
closed-file publication and temporary staging were added rather than weakening
the reread gate. The low-level cause of the earlier file truncation is not proved
by this evidence; it is not attributed to source content or GitHub CI.

Seven regressions cover serialized unknown fields, order and origin associations;
deterministic file/report bytes; raw/logical hash refusal; unaccepted passports
and output reuse; corrupt pool contents even after updating their file hash;
out-of-range/boolean references; bounded baseline writer lifetime; and interrupted
writers exposing no finished asset. The original built-pack storage test also
remains applicable and unchanged.

## Reader boundary and next handoff

Source inspection distinguishes physical storage evidence from installation:

| Area | Current contract | Required next work |
| --- | --- | --- |
| JSONL shape | PackChunk requires legacy primary fields and tokens; selected rows lack six of those required fields | Add a pinned selected-to-pack materializer, without reapplying selection |
| Context ordering | Selected `contexts` includes primary; PackChunk stores primary in root fields and alternatives in `contexts` | Flatten the first context deliberately and retain every ordered alternative |
| Offsets | The importer uses UTF-16 code units | Resolve the single complete canonical token using the pinned segmentation environment; preserve source spelling and targetId |
| Token classification | Existing token_list uses the exact rank-based top-function-word set | Supply the exact top 60 forms per language from the admitted rank space; exported target ranks cannot reconstruct excluded function words |
| Morphology | Snapshot lemma evidence is unavailable; rule v1 has conservative fallback | Make the fallback/enrichment profile explicit; do not invent evidence or alter target identities |
| Credit presentation | Current source parser recognizes the final Tatoeba sentence-ID suffix | Preserve compatibility credit behavior while retaining known names, version and every origin separately |
| Persistent provenance | PackContext and ChunkContextEntity retain sourceFamily/contextId/meaningId, not a structured origin list/version/contributors | Design a lossless client handoff and any explicit Room migration before claiming installed evidence survives |
| Size limits | CatalogFetch caps each compressed and logical deck at 24 MiB and index at 2 MiB | Measure actual enriched final assets/index and actual runtime import; intermediate sizes are insufficient |
| Existing trimming helper | Historical builder may remove optional alternatives to fit raw cap | Do not invoke it on the frozen snapshot; report any real final overflow rather than silently deleting contexts |

No runtime model or new corpus is needed to derive spans and conservative token
fields. The missing rank evidence can be exported from the separately retained
raw admitted pool, without repeating acquisition or selection. A small pinned
function-word metadata export is sufficient for the current token classifier;
this does not require adopting a new frequency space from the selected subset.
Any richer morphology inputs remain subject to their existing admission rules.

Adding arbitrary origin JSON as an unknown pack key is insufficient: current
Kotlin models ignore unknown keys, and PackLoader does not persist that data.
This is a source-audited handoff limitation, not a new runtime failure observed
in the app. Source hashes and exact checks belong to the reader-boundary evidence.
No JVM/Android import, app performance, RAM or installed Room size is claimed.

## Reproduce

```bash
python3 tools/catalog/test_selected_storage_experiment.py
python3 tools/catalog/selected_storage_experiment.py \
  --selected selected-everyday.jsonl.gz \
  --snapshot docs/evidence/everyday-round15/SNAPSHOT.json \
  --output selected-storage-measurement
```

Use the exact full selected member of run `37674241928`; do not substitute its
preview, a pool or the old capped build. Only standard-library Python is needed.
The output retains assets plus `STORAGE.json`/`STORAGE.md`; keep enough free scratch
space for the temporary index and three layouts. A failed run needs a fresh output
directory. Existing successful outputs and the original selected archive remain
untouched.

The full source ZIP contains the reusable tool, tests, small evidence reports and
updated owning documents. Derived large measurement assets are reproducible from
the separately retained selected archive and are not packaged as app assets.

## Acceptance scope

The inherited 220 MiB total / 24 MiB cold transfer thresholds are exploratory
comparators for **Everyday intermediate** bytes. A provisional 10% savings screen
avoids treating a small intermediate gain as sufficient reason to migrate client
storage; it is a reversible experiment heuristic, not a new product/release gate.
Language pooling is diagnostic regardless of size because one deck can depend on
many unwanted decks' data.

Final token payloads, target-local spans, compatibility credits and provenance
representation can change reuse and bytes. Reconstructed logical selected bytes,
whole cold dependency bytes, transient RAM and installed database bytes are
different quantities. Global Catalogue and final client/publication acceptance
remain open after an intermediate measurement.

## Checks and immediate next input

Executed checks: seven new storage regressions, the existing built-pack storage
test, full three-layout serialized reread/reconstruction, input/deck/evidence
hash reconciliation, source/Room/reader-contract cross-check, repository text,
changed-document links/whitespace, final scoped diff and full source-ZIP
manifest/byte/CRC verification. App/JVM/Android builds are not run for this
offline-tooling round; no application source or Room schema is changed.

To measure final self-contained and pair-pack payloads next, supply the retained
`catalogue-v2-everyday-admitted-pool` archive containing `everyday-candidates.jsonl.gz`
and its report (run `37458524156`, pool SHA-256
`57b90b01d5dcb0725743998dccaf307a100e174059a853e01f5f31fc13dd6170`),
or an equivalent input-bound export of the original per-language top-function-word
sets. This is a different input from the complete selected archive already
supplied. Derive the small rank prefix with the original context-key/ordering
contract; no reacquisition, selection, semantic review or three-hour workflow is
needed. This input gap was established by reader inspection, not by a storage
failure. Do not classify every unknown-rank token as content to bypass it.

Subsequent checkpoint, 2026-10-09: the owner supplied that admitted archive in
split form. [ROUND-0.12-19.md](ROUND-0.12-19.md) records its full identity scan and
exact reusable frequency-prefix export, closing this input gap. This historical
record's original measurement and reader-acceptance limits remain unchanged.
