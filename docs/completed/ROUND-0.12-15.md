# Round 15 — full Everyday census and selected snapshot

2026-10-08. Owner-approved audit of the saved full Everyday output. No acquisition,
selection, source admission, application change or workflow dispatch is performed.
The source baseline is `ikna-0.12-press-round-14-size-scenario.zip`.

## Decision and scope

Accept the exact selected Everyday intermediate for non-publishing Part 12
lossless-storage and client measurements. Its content, policy and input pins are
fixed by [SNAPSHOT.json](evidence/everyday-round15/SNAPSHOT.json). This closes the
Everyday selected-material census/snapshot checkpoint, not the entire Catalogue
Part 11 or release gates. World provenance, Knowledge acceptance, final packs,
client acceptance and publication remain separate.

The historical workflow report still says `contentFrozen=false`,
`materialReviewCompleted=false`, `semanticAccuracyCertified=false` and
`publicationSafe=false`. Its bytes were not rewritten. The new
`everydaySelectedSnapshotFrozen=true` decision has the narrower measurement scope
above; it is not a semantic certificate or permission to publish.

Keep the 8,000 cap and the classic card flow. Replenishment research and the
10k/12k/progressive-download scenarios stay separately scoped and unscheduled.

## Exact input and full verification

The owner supplied `catalogue-v2-everyday-selected-material.zip` from successful
run [37674241928](https://github.com/d1d2dopamine/ikna/actions/runs/37674241928).
The 256,032,060-byte ZIP passes CRC verification and contains exactly the full
selected file, its selection report and input reference. The embedded report is
byte-identical to the report retained in round 12.

| Material | Bytes | SHA-256 |
| --- | ---: | --- |
| Selected gzip | 255,938,165 | `a2a5e4bb1e4079e88c8986443c95761795d5fe66ec14ee46a1f83e099e95df64` |
| Uncompressed selected JSONL | 1,512,254,677 | `7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4` |
| Locally regenerated quality report | — | `0af66456924a2bfe37f7030eb32e3fddd11cff80e1236f6c41ab9703e0168a29` |

Run the unchanged `selection_evaluate.py` over every selected row, with the exact
saved baseline and its expected report hash. Python 3.12.14, ICU 74.2,
SudachiPy 0.6.10 and SudachiDict-core 20250825 match the successful workflow;
the dictionary digest is pinned in the snapshot. No dependency download is needed.

The local evaluator exits successfully and reproduces the retained workflow
`QUALITY.json` byte for byte. Checks cover raw/logical identities, all row counts,
target/context identities, within-deck uniqueness, source registry/version/credits
contracts, quarantine exclusions, lengths, UTF-16/token offsets, Japanese
morphological boundaries, retained-alternative duplicate policy and complete deck
scope. The 1,424 similar alternative pairs remain the report's informational
count, not new unreviewed failures.

This verifies the selected file, not acquisition completeness of an arbitrary
Tatoeba export or correctness of every translation. The original admitted pool
is separately saved by the owner; its raw bytes were not supplied or reread in
this round. Repeating acquisition or selection would not improve this evidence.

## Full census

A separate read-only pass over all rows reconciles the selection and evaluator,
and records every included deck plus both excluded decisions. Machine counts and
the full table are retained in [CENSUS.json](evidence/everyday-round15/CENSUS.json)
and [CENSUS.md](evidence/everyday-round15/CENSUS.md).

| Measure | Result |
| --- | ---: |
| Included deck memberships | 928,162 |
| Unique global learning targets | 221,653 |
| Retained contexts | 2,094,192 |
| Origin occurrences | 2,094,728 |
| Unique primary source sentence references across learning languages | 1,247,700 |
| Unique primary source texts across learning languages | 1,247,657 |
| Unique directed candidate IDs | 1,667,271 |
| Included decks | 328 |
| Normal / thin / omitted decisions | 257 / 71 / 2 |

All 110 language directions retain at least one level. The omitted pl-ko middle
and advanced decisions retain their historical pre-omission counts of 32 and 30.
The [round-12 shortage diagnosis](ROUND-0.12-12.md) is unchanged: corpus supply,
allocation loss and the safety cap are distinct; no below-1000 deck is caused by
the 8,000 budget.

Contexts per membership are one for 287,645 entries, two for 115,004, and three
for 525,513. About 31% therefore have only one retained example. They are explicit
limits of this snapshot, not instructions to pad or generate examples. Memberships
in different decks are not separate global learner memories.

All origins are Tatoeba, version `2026-10-03-2d4eee105639ba24`. Contributor names
are present for 2,007,473 context-side and 1,995,071 meaning-side origin
occurrences. Names are optional under the pinned registry; known names and exact
sentence links must survive storage. Never invent missing names. Full source
policy checks pass; this census does not grant broader rights than that policy.

All 928,162 memberships mark lemma evidence unavailable in this intermediate.
Final pack enrichment and reader validation remain open; this output is not an
installable pack. Boundary validation did not rewrite targets or supply lemmas.

The 255,938,165-byte whole-file gzip includes metadata, contexts and provenance.
The largest deck's logical intermediate rows occupy 17,589,103 bytes. Neither
figure is a final per-deck download or installed database size. Part 12 must
measure an actual lossless layout before making user-facing MB claims.

## Bounded allocation witness

Retain [ALLOCATION-WITNESS.json](evidence/everyday-round15/ALLOCATION-WITNESS.json)
for 81 existing direct Polish/Korean bilingual witnesses from included pl-ko and
reversed ko-pl context/meaning evidence. This reuses existing text and source
references, without generating translations. It uses 21,493 exported Polish
surface ranks; no rank-ambiguous surfaces are found. Eight witnesses fail length
and one has no known eligible choice, leaving 72 qualifying witnesses.

| Level | Known eligible targets | Current greedy, up to 3 contexts | Greedy, primary only | Maximum matching, primary only |
| --- | ---: | ---: | ---: | ---: |
| Beginner | 117 | 51 | 57 | 60 |
| Middle | 46 | 32 | 32 | 32 |
| Advanced | 37 | 26 | 26 | 26 |

The beginner replay reproduces all 51 actual selected target IDs **and their
exact context assignments**. The primary-only variants demonstrate that reserving
alternatives and ordering can reduce coverage in this example. Their 57/60 counts
change context retention assumptions: they do not prove nine recoverable cards
at the current quality/profile or justify changing the selector now.

This is not the complete raw pool: it omits discarded candidates and unexported
rank surfaces. Advanced covers only 37 known eligible targets versus 47 in the
full report, so 26 must not replace the original selected/pre-omission count 30.
Even the middle match does not prove an optimum over all raw evidence. No global
recoverable-yield estimate can be inferred. A separately scoped allocation study
needs the saved admitted pool and exact rank construction; it is not a blocker
for measuring the accepted selected snapshot.

## Reproduction, retention and checks

After explicit extraction of the two owner-supplied artifacts, the full evaluator
command was:

```bash
python3 tools/catalog/selection_evaluate.py \
  --selected selected-everyday.jsonl.gz \
  --selection-report EVERYDAY-SELECTION.json \
  --baseline-directory baseline \
  --baseline-report-sha256 c8afd1559893fca2e336a784677a25428e43423886c33713917c261cd116fc7a \
  --output-dir evaluation
```

The baseline directory is the extracted `catalogue-v2-everyday-selection-review`
artifact with its `reports/` and `review/` paths. Matching analyzer dependencies
must be available on PYTHONPATH. The snapshot retains input, report, registry,
pipeline and environment pins; round-12 report bytes remain authoritative.

Exact one-off read-only census/allocation drivers are archived as
`CENSUS-ANALYSIS.py.gz` and `ALLOCATION-ANALYSIS.py.gz` beside their outputs.
[CENSUS.md](evidence/everyday-round15/CENSUS.md#reproduction-and-scope) describes
the captured workspace paths and replay order. These are evidence recipes, not a
new production build engine or workflow. Scratch SQLite indices and derived raw
witnesses are reproducible and excluded from the project ZIP.

The original large selected archive stays separately retained by the owner;
the full source ZIP contains reports, pins and analysis recipes, without a second
copy of the corpus. Checks for this batch: full evaluator, independent census
reconciliation, exact 51-target/context replay, evidence hashes, text check,
document links/whitespace, and full source-ZIP manifest/bytes/CRC verification.
No app build or platform test is claimed for documentation-only changes.

## Next work

Proceed to Part 12 on these exact saved rows: compare lossless storage layouts
and measured per-deck/full-set download and unpacked sizes; round-trip every
identity, context, offset and provenance field. Then validate the final pack
handoff, enrichment requirements and actual app-reader limits. Do not reapply the
legacy sieve or silently drop alternative contexts/credits to reduce size.

Raw-pool allocation research and incremental WMT24++/MKQA admission/yield
measurements remain independent later tasks. The selected snapshot can already
serve storage measurements without another multi-hour selection workflow.
