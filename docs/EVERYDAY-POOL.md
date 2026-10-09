# Admitted Everyday pool — Part 7

The active [0.12 plan](PLAN-0.12.md) retains the reviewed decision: Tatoeba is
the admitted Everyday source; MASSIVE is excluded. The existing
`everyday_rebuild.py` and Everyday experiment workflow remain Part 6 comparison
tools. Their combined ranks and preview are not the final Tatoeba-only pool.

`tools/catalog/everyday_pool.py` consumes **local normalized candidate files**.
It validates every origin against the audited Tatoeba policy and one explicitly
pinned source version, exact-deduplicates candidates while retaining all origins,
and saves the complete requested candidate scope. There are no network requests,
deck assets, publication steps, synthetic filler or changes to target identity.

The report records SHA-256/size for each input and the pool, logical JSONL identity,
registry/pipeline hashes, Python/zlib/segmentation evidence, source version, scope,
duplicates and rows outside scope. Every requested directed pair appears, including
zero-supply pairs, with eligible targets by level and sieve rejection counters.
Global exact target count is separate from summed pair/level memberships.

Coverage uses the existing builder's primary-origin context and shortest preferred
meaning rule. Alternative merged origins and other meanings remain in the full
pool for later selection. Eligible target-context memberships are pre-selection
evidence, not retained contexts or extra learner cards. Deterministic samples are
review aids, never a replacement for the full pool.

Identical ordered input bytes, scope, pipeline and segmentation environment yield
identical pool bytes; gzip has no timestamp or filename. Input order is pinned by
the report because first-seen order still matters for frequency ties and primary
origins. A complete input scan means EOF of the supplied files. It does **not**
prove that an arbitrary small file represents the entire Tatoeba export.
`sourceAdmissionPassed=true` therefore does not set `publicationSafe=true`:
acquisition completeness, selected-material review, freeze and release gates remain.

## Local reuse

Use a previously retained **complete normalized candidate file**, its source
version and digest. Do not feed selected pack rows, aggregate census reports or
an experimental mixed-source pool to this tool.

```bash
python3 tools/catalog/everyday_pool.py \
  --candidates tatoeba.jsonl.gz --source-version "$TATOEBA_VERSION" \
  --expect-sha256 "$TATOEBA_SHA256" \
  --pool everyday-candidates.jsonl.gz --staging everyday-pool.sqlite3 \
  --json EVERYDAY-POOL.json --markdown EVERYDAY-POOL.md \
  --samples EVERYDAY-SAMPLES.md
```

The version/digest variables identify actual retained data, not placeholders.
`--learn` and `--meanings` default to all supported content languages;
`--expect-sha256` accepts one digest per input in the supplied order. Validation
rejects mixed/unadmitted origins, foreign collections, moving version labels,
invalid references, mismatched pins and input/output path aliases. A bad input
does not replace the previous successful pool.

## Workflow without implicit reacquisition

The manual `catalogue v2 everyday admitted pool` workflow defaults to `artifact`:
provide the exact run ID, artifact name, candidate basename, source version and
candidate SHA-256. It downloads that artifact only; a missing or expired artifact
fails without falling back to a corpus download or a latest-run lookup.

`fresh-tatoeba` is a separate explicit input choice for genuinely missing final
inputs. It acquires the detailed sentence export with contributor metadata and
direct links through the existing retry/resume helper. Both archive digests and
Last-Modified values are recorded; the source version binds the sentence export
date to a hash of both inputs. This workflow was not dispatched during round 02.

The artifact `catalogue-v2-everyday-admitted-pool` contains the full
`everyday-candidates.jsonl.gz`, `EVERYDAY-POOL.{json,md}`, review samples and
`INPUT-REFERENCE.json`, retained for 90 days. For the next reuse, take the version
and `pool.sha256` from that report. Preserve a reviewed snapshot before its Actions
retention expires. With `run_selection=true` (the default), the same workflow
then performs the admitted Part 10 review described below. Pool upload happens
**before** selection, so a failed selection can reuse this run's version/hash
in artifact mode rather than downloading the corpus again.

## Admitted Part 10 review

`tools/catalog/everyday_selection.py` consumes the full pool and its exact
`EVERYDAY-POOL.json`. It verifies raw/logical hashes, sizes/counts, the admitted
Part 7 status and registry pin, then checks every candidate/origin against the
current Tatoeba policy, source version and recorded language scope. Report and
pool changes during the run fail closed. Duplicate candidates contradicting the
report are rejected; input/report/registry aliases cannot be overwritten.

Selection uses precisely the report's learning/meaning scope and function-word
sieve. Every directed pair and level appears, including zero-source omissions.
Reports separate eligible/selected/budget/context-collision counts. No shortage
is padded; exact target identity and provenance remain unchanged.

```bash
python3 tools/catalog/everyday_selection.py \
  --pool everyday-candidates.jsonl.gz --pool-report EVERYDAY-POOL.json \
  --staging everyday-selection.sqlite3 \
  --json EVERYDAY-SELECTION.json --markdown EVERYDAY-SELECTION.md \
  --preview everyday-selection-preview.jsonl.gz \
  --samples EVERYDAY-SELECTED-SAMPLES.md
```

The workflow retains `catalogue-v2-everyday-selection-review` for 90 days:
selection JSON/Markdown, selected-material samples, bounded JSONL preview,
the bound pool report and input reference. The full pool is in the separate
same-run `catalogue-v2-everyday-admitted-pool` artifact. Current v2 defaults are
12,000 maximum targets per level, 40 minimum, 1,000 thin-deck threshold and
three contexts per target. To replay the historical selection, explicitly pass
`--max-deck 8000`; existing evidence and source passports remain unchanged.
Review rows are limited to the first ten policy-ranked
targets per publishable deck; they are not full selected assets or representative
quality statistics. Contributor metadata remains in preview origins and samples.
Native-speaker/reliability or translation correctness is not established by
passing this automated gate. Human review/freeze/publication remain open.

## Saved preview audit and card review — round 09

The successful run [37458524156](https://github.com/d1d2dopamine/ikna/actions/runs/37458524156)
now supplies both artifacts. The owner has saved the large pool; do not request
fresh Tatoeba acquisition or another identical selection to inspect the preview.
Use the **small** `catalogue-v2-everyday-selection-review.zip` below, not the
545 MB admitted-pool artifact. `selection_audit.py` is offline and performs no
network requests, corpus rebuilds, source rewrites or publication.

```bash
python3 tools/catalog/test_selection_audit.py
python3 tools/catalog/selection_audit.py \
  --review-zip catalogue-v2-everyday-selection-review.zip \
  --expected-sha256 22e0f8372d09e70993db4b2aacf787b913c44379a21ec823ae1551ef7eb4fded \
  --notes docs/evidence/everyday-round09/MANUAL-REVIEW.json \
  --output-dir review-output
node tools/catalog/test_selection_review.cjs review-output/CARDS.html
```

The output directory must be new: input files and old reviewer decisions are not
overwritten. ICU must match the source report (74.2 for this artifact). Python's
standard library plus the existing system ICU suffice for the auditor; Node is
optional for the viewer logic regression, and no JS dependency installation is
needed. For a future different artifact, use its own exact ZIP hash and omit
these round-specific `--notes` or supply new notes bound to that artifact.

Outputs are `AUDIT.json`, `AUDIT.md` and the self-contained `CARDS.html`. The
delivered [round-09 viewer](evidence/everyday-round09/CARDS.html) already contains
all 3,280 supplied preview memberships, so regeneration is optional. Download
and open it in a browser to see actual target/context/meaning text. Filter by
language pair, frequency level, review signals, manual notes or text; reveal the
meaning and inspect original Tatoeba references/contributors. Each context uses
its own exact surface and UTF-16 highlight, including case variation.

Viewer ratings/comments are separate review data. They do not alter Catalogue
assets or FSRS. Save **Скачать решения JSON** before handing work back or closing
the viewer: local browser storage can be unavailable or cleared. Import replaces
the viewer's current decisions only after checking the exact artifact/preview
hashes, source version and known memberships; foreign/invalid decisions leave
current work untouched. Unreviewed comment drafts are retained without inventing
a verdict. Imported reviewer comments render as plain text.

Audit checks distinguish 754 unique global targets from 3,280 memberships and
8,157 contexts. They validate hash links, inventory and summaries, current
admission policy, all origin families/versions/references, candidate/target IDs,
one complete canonical token per context, UTF-16 reconstruction and the existing
production near-duplicate contract. Reproducibility metadata records the checker,
template, policy and segmentation environment.

Single-hiragana Japanese targets and near-edit alternative contexts are **review
signals only**. The auditor does not ban Chinese/Korean one-character targets,
infer senses/lemmas, assess CEFR, score translation truth or approve material.
The full pool hash is linked through the supplied reports, not independently
recomputed without the large input. First-ten ranked previews cannot estimate a
corpus-wide defect rate or establish a final material freeze. The 27 curated
findings are a preliminary, deliberately selected inspection, not a native-speaker
certification of eleven languages. See [ROUND-0.12-09.md](ROUND-0.12-09.md).

## Next single run: quality handoff

Owner decision, 2026-10-07: round 10 ends routine manual-selection work. The owner
need not assess unfamiliar languages or every card; round-09 viewer/notes are
optional historical aids. Continue through automated checks and the later stages.

After committing the round-10 fix-1 ZIP, start a new dispatch (not Re-run jobs
on the old commit): choose **Actions → catalogue v2 everyday quality
handoff → Run workflow**. Leave the five prefilled inputs unchanged:

| Input | Exact checkpoint |
|---|---|
| input_run_id | `37458524156` |
| candidate_sha256 | `57b90b01d5dcb0725743998dccaf307a100e174059a853e01f5f31fc13dd6170` |
| pool_report_sha256 | `41c7e176d516e6ad89d70954b116d18062baa48857f66ee276bdcfd32dd0438f` |
| baseline_report_sha256 | `c8afd1559893fca2e336a784677a25428e43423886c33713917c261cd116fc7a` |
| source_version | `2026-10-03-2d4eee105639ba24` |

The workflow reuses the exact saved pool/review artifacts, verifies pins, selects
**once** with `boundary-diversity-v2`, streams complete included memberships and
checks that full output. It does not rerun the old selector, rebuild admission,
fetch Tatoeba or fall back to latest/fresh when an artifact is missing/expired.
This supplies new evidence for changed policy and the full Part 11/12 handoff.
The Japanese analyzer/dictionary are pinned offline tools; see MORPHOLOGY. Two
observed Spanish source cases are deferred by exact reference/version/text hash,
including their use as meanings; originals and unrelated regional forms remain.

Retained artifacts (90 days):

- **catalogue-v2-everyday-selected-material**: full `selected-everyday.jsonl.gz`,
  exact selection report and input reference. Save for census/storage. Uploaded
  before evaluation so a failed check can reuse it without reselection.
- **catalogue-v2-everyday-quality-report**: small reports/preview and
  `QUALITY.json/md`. Send this result/link back; uploading the large material is
  unnecessary just to inspect metrics. Before/after examples are source-bound text.

The evaluator verifies raw/logical hashes, unique memberships/contexts, source
provenance, exact target/candidate identities, complete token/UTF-16 boundaries
and the Japanese guard. Counts compare every deck. Exact old target/context
retention is measured only for the baseline preview, not claimed globally.
Losing a previously included language pair stops automatic progression; shortages
are never padded. `qualityGatePassed` certifies these automatic contracts, while
semantic certification, content freeze and publication flags remain false.

After success, proceed to complete selected-material census/snapshot and then
storage/client validation. Do not add routine owner manual review or resieve the
original pool through the legacy builder, reintroducing deferred occurrences.
Existing selection CLI defaults remain legacy-compatible; local reuse passes
`--quality-policy boundary-diversity-v2 --selected-output selected-everyday.jsonl.gz`
to the admitted selection command above. Then run:

```bash
python3 tools/catalog/selection_evaluate.py \
  --selected selected-everyday.jsonl.gz --selection-report EVERYDAY-SELECTION.json \
  --baseline-directory extracted-old-review \
  --baseline-report-sha256 c8afd1559893fca2e336a784677a25428e43423886c33713917c261cd116fc7a \
  --output-dir quality-results
```

`--selected-output` is independent of the preview limit. Its admitted handoff is
deterministic and input/registry/output aliases are rejected. A failed admitted
selection preserves prior saved material. Evaluation output must be a new
directory. Full quality evidence is pending this run, not supplied by local fixtures.

Generic selection now rejects input/output aliases (including hard links),
fails on damaged UTF-8, and writes deterministic gzip bytes without output-name
or timestamp metadata. The admitted wrapper also retains a previous staging file
if validation/selection fails. No public Catalogue asset/index is generated.

## Successful quality handoff and coverage checkpoint — 2026-10-08

Run [37674241928](https://github.com/d1d2dopamine/ikna/actions/runs/37674241928)
completed the corrected saved-pool selection and full-output automatic gate.
It reuses pool run `37458524156` and source `2026-10-03-2d4eee105639ba24`.
This supersedes the pending-run instructions above and the old round-08 input
availability checkpoint below; neither is a request to acquire or select again.

Retain `catalogue-v2-everyday-selected-material` from this exact new run. The
selected gzip is 255,938,165 bytes, SHA-256
`a2a5e4bb1e4079e88c8986443c95761795d5fe66ec14ee46a1f83e099e95df64`.
It contains 928,162 memberships and 2,094,192 contexts in 328 included decks.
The raw admitted pool saved earlier is not this selected-material artifact.

[ROUND-0.12-12.md](ROUND-0.12-12.md) records local report checks and limitations;
[coverage evidence](evidence/everyday-round12/COVERAGE.md) separates 53 decks with
post-sieve supply below 1,000 from 20 that cross below 1,000 during distinct-context
allocation. No weak deck loses targets to the safety budget. Polish with Korean
meanings has only 94 candidate rows and two levels omitted below 40. The report
does not establish optimal context assignment or completeness of every possible
Tatoeba export.

The next-checkpoint statement recorded in round 12 is now superseded by
[ROUND-0.12-15.md](ROUND-0.12-15.md): the owner supplied the full selected archive,
all rows were locally checked, the quality report was reproduced byte for byte,
and the selected Everyday snapshot is accepted for non-publishing Part 12
measurements. Its [passport](evidence/everyday-round15/SNAPSHOT.json) and
[full census](evidence/everyday-round15/CENSUS.md) bind exact bytes and limitations.
The original raw pool is a different retained input, not reread in round 15.

Next is lossless storage measurement on these exact selected rows, followed by
final pack/client acceptance. Corpus replenishment, semantic certification,
global Catalogue freeze and public publication remain separate. Do not weaken
quality rules or add filler to make deck counts equal. No new routine
manual-review round or selection rerun is required.

## Selected snapshot storage measurement

Use the full selected member from run `37674241928`, not its preview or raw
candidate pool. The round-15 passport fixes the exact compressed/logical bytes
and accepts this intermediate for non-publishing Part 12 measurements:

```bash
python3 tools/catalog/selected_storage_experiment.py \
  --selected selected-everyday.jsonl.gz \
  --snapshot docs/evidence/everyday-round15/SNAPSHOT.json \
  --output selected-storage-measurement
```

The output directory must be new and separate from inputs. Only the Python
standard library is needed; no corpus acquisition, segmentation or selection
is performed. The disposable SQLite stage preserves original within-deck order
and bounds open baseline gzip writers to one deck. The tool writes all 328
self-contained intermediate files, pair pools and learning-language pools, then
rereads actual files and restores every row including unknown fields, ordered
contexts, origin/meaning associations and names. Raw/logical input hashes,
deck counts, serialized file identities and reconstructed deck digests are
checked before `STORAGE.json` is emitted. Failed/partial output is not evidence;
use a fresh directory for retry, without overwriting inputs or successful output.

`--modes pair` or `--modes language` limits exploratory layouts. Pair pools share
only within the directed language pair; language pools are diagnostic and can
require many unwanted decks' data for one cold install. The tool keeps aligned
`candidateId`/`origins` on each occurrence reference, never pools them by sentence
ID alone. Core/meaning pooling uses exact full payload bytes, not text-only joins.

The compact baseline uses JSON whitespace removal and gzip level 9 with `mtime=0`.
The source artifact used level 6; different sizes are not evidence of removed
cards. A provisional pooling screen requires at least 10% savings plus inherited
220 MiB total / 24 MiB cold transfer thresholds. This screen is a conservative
experiment heuristic, not a new production gate: these are Everyday-only
intermediate bytes. Final token/credit/enrichment payloads, index size, app RAM,
installed database size and real-platform import still require measurement.

The output is not `PackChunk`: selected `contexts` includes the primary, while
the reader's list contains alternatives after legacy primary fields. Required
tokens, canonical-token UTF-16 spans and compatibility translations must be
materialized without re-selection. Exact top-function-word evidence can be
exported from the retained admitted pool without repeating selection; exported
target ranks alone do not identify the omitted top 60 function words. Preserve
known source version, every origin and contributor through the final handoff;
unknown JSON keys alone do not make the current importer retain that evidence.

## Historical next-owner-run instructions (0.12 round 08; superseded)

The 2026-10-06 public API checkpoint lists no retained final admitted-pool
artifact, and the old Everyday experiment lists no artifacts. Its declaration
had not retained full candidates. Local fixtures are not production inputs.
Full final Tatoeba input therefore remains unavailable to this session.

After committing the complete round-08 ZIP to the default branch, open Actions →
**catalogue v2 everyday admitted pool** → Run workflow. If no complete normalized
input was separately saved, choose `input_mode=fresh-tatoeba`, keep both language
lists at their eleven-language defaults, and leave `run_selection=true`.
Artifact-specific run/version/hash fields are unused in this mode. This obtains
the missing final admitted input, not another Part 5 census or MASSIVE admission
experiment. No World or Knowledge experiment is requested for this round.

If a complete candidate input is already available in an Actions artifact,
prefer `artifact` with its exact run/name/basename/version/hash. Missing artifacts
still fail without a fresh fallback. Send the resulting run link plus both
artifacts (or at least `EVERYDAY-POOL.json`, `EVERYDAY-SELECTION.json`,
`EVERYDAY-SELECTED-SAMPLES.md` and `INPUT-REFERENCE.json` for initial review).
Preserve the full pool locally; aggregate reports/previews cannot recreate it.
If selection fails, the prior pool-upload step determines whether the saved
input can be reused. See [ROUND-0.12-08.md](ROUND-0.12-08.md) for evidence limits.

The old Everyday experiment currently retains reports and a bounded selection
preview; the supply census retains metrics/ranks, not raw candidates. Those outputs
cannot reconstruct the final uncapped pool. Existing run evidence is catalogued
in [ROUND-0.12-02.md](ROUND-0.12-02.md); completed admission decisions stay closed.
