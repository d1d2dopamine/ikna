# Round 20 — 12,000 ceiling and pinned replenishment diagnostics

2026-10-09, Asia/Yekaterinburg. The owner authorized both parts of the reviewed
round. Start: complete round-19 plan-update ZIP, 644 source files, SHA-256
`41dac97364eedfc585332a6a11da0fdeb5484ab9500098406e7f289225b9cf9f`.
Every baseline member matched before editing. No Git checkout/tool installation,
application version/schema change, public upload or full Catalogue build.

## Approved budget and available growth

Catalogue v2's maximum/default is **12,000 exact target memberships per deck**.
`catalogue_core.V2_MAX_DECK_TARGETS` owns the Python ceiling. Selection, admitted
Everyday selection, supply census/shards and the v2 builder use the same argument
validator. Four dispatch workflows now default to 12,000 and validate the budget
before corpus work. The admitted-pool workflow inherits the updated selector
default. Explicit smaller budgets, including 8,000, remain valid for historical
replay. The separate v1 publisher is unchanged. The builder's prior standalone
3,000 default is reconciled with the v2 workflow rather than left inconsistent.

This is a ceiling, never a quota. Quality, minimum 40, thinness diagnostic 1,000,
three-context policy, exact identity and licence gates are unchanged. Progressive
download remains unscheduled. Neither the 24 MiB transfer/logical import caps nor
the 2 MiB index cap is raised. No frozen material passes through the historical
alternative-context trimming helper in this round.

[BUDGET-12000.json](evidence/everyday-round20/BUDGET-12000.json) uses every saved
round-12 deck and the actual round-16 intermediate storage report. There are 45
decks at the old cap. For each, additional memberships are bounded by
`min(4,000, budgetRejected)`: at most **161,978**, giving **1,090,140** included
Everyday memberships from the unchanged source/rank/allocation inputs. This
tightens the earlier coarse 180,000 ceiling. Remaining candidates may collide;
the true new count has not been measured. Membership gains are not unique global
target gains. All 73 below-1,000 decks have zero budget rejection and remain
unaffected by this cap change alone.

The constant-bytes-per-row stress screen finds no self-contained intermediate
deck above 24 MiB at its count upper bound. That is a hypothetical screen, not a
measured new download, final enriched-pack size, RAM or installed database size.
Actual final bytes and reader acceptance remain Part 12 gates; report overflow
instead of silently deleting accepted learning material.

## Acquired evidence and comparable frequency space

Publisher data/terms were reread and candidate bytes acquired locally. Exact
revisions, immutable download URLs, file sizes and SHA-256 are retained in
[FILES.json](evidence/everyday-round20/FILES.json) and
[REVISIONS.json](evidence/everyday-round20/REVISIONS.json). No production registry
entry is added or made ready. Full third-party corpus files are not source-ZIP
members; the pins permit reacquisition without another Tatoeba export/workflow.

For the focused Polish/Korean comparison, the saved raw pool was streamed once:
11,099,490 rows / 5,320,249,214 logical bytes matched its existing logical hash.
Only Polish/Korean contexts and the 188 reciprocal pair rows were retained.
Frequency ranks use **selection_experiment.build_ranks**, not the differently
ordered builder function-word export: normalized context hashes sorted in key
order, lowercase counts and first-seen frequency ties. There are 123,779 normalized
Polish and 15,336 Korean contexts. The occurrence-based round-19 Polish count
123,831 is a different measure, not a discrepancy to correct.

[BASELINE-PASSPORT.json](evidence/everyday-round20/BASELINE-PASSPORT.json),
[PL-KO-RANKS.json.gz](evidence/everyday-round20/PL-KO-RANKS.json.gz) and
[PL-KO-BASELINE.jsonl.gz](evidence/everyday-round20/PL-KO-BASELINE.jsonl.gz) retain the
small hash-bound inputs. Original Tatoeba origins/contributor names remain in the
pair rows; licence/credit policy stays in the existing source registry. All six
original eligible/selected/context-collision counts reproduce exactly before
measuring additions. New forms absent from baseline ranks are reported before
the sieve and deferred, not silently assigned an advanced level.

## Corpus decisions

The detailed distribution/credit decisions are in
[CORPUS-CANDIDATES-0.12.md](CORPUS-CANDIDATES-0.12.md#round-20--acquired-licence-and-yield-review).

- **WMT24++:** keep first priority for bounded Everyday replenishment. All ten
  target-language files share the same 998 source/document/segment anchors;
  English makes eleven languages. Post-edited human `target` references are used.
  38 bad-source segments and 355 outside-scope segments are excluded, retaining
  605 whole social/speech segments. No punctuation splitting or machine output.
  Some social text is fragmentary, highly informal or context-dependent: domain
  and length labels alone do not approve it for study. Keep production admission
  off pending a documented usable-content subset and attribution handoff.
- **MKQA:** primary dataset terms are CC BY-SA 3.0, not its Apache-2.0 code
  licence or the mirror's CC BY 3.0 label. The 10,000 queries have supported
  translations and unique example anchors. All-query results below are an
  optimistic technical diagnostic **before** Everyday/domain acceptance.
  Fact/entertainment/entity questions and search-style fragments make wholesale
  Everyday admission unsuitable. A useful question subset needs explicit rules;
  answers, aliases and passages are never meanings. Keep conditional priority 2,
  not an approved 10,000-query content import.
- **NTREX-128:** retain as a Knowledge candidate under CC BY-SA 4.0. The acquired
  English/Polish/Korean files and document IDs have 1,997 aligned lines across 123
  documents. Length-only screening leaves 1,920 Polish-learning and 1,272
  Korean-learning pairs. That is not selected-card or net-Knowledge yield; other
  languages are listed upstream but not measured here. No Everyday gap claim.

## Bounded result, not release acceptance

[REPLENISHMENT.json](evidence/everyday-round20/REPLENISHMENT.json) records six trials,
input hashes, pipeline hashes, ICU 74.2, source/shape rejections and unknown forms.
Exact duplicate candidates merge **all origins before selection**. Target ranking,
bounded evidence, canonical boundaries, near-duplicate suppression and distinct
context allocation reuse current selector functions; morphology is unavailable as
in the accepted baseline. Trial-only mixed-source evidence is not a licensed
production pack. No semantic certification or full-matrix coverage claim.

| Learning → meaning | Level | Original | With WMT social/speech | With all MKQA queries | With both |
| --- | --- | ---: | ---: | ---: | ---: |
| pl → ko | beginner | 51 | 259 | 854 | 930 |
| pl → ko | middle | 32 | 286 | 1,193 | 1,339 |
| pl → ko | advanced | 30 | 376 | 3,511 | 3,775 |
| ko → pl | beginner | 55 | 222 | 640 | 713 |
| ko → pl | middle | 42 | 189 | 766 | 861 |
| ko → pl | advanced | 45 | 226 | 1,285 | 1,429 |

WMT's technical candidate subset lifts both omitted levels above 40, but no level
reaches 1,000. It shows credible replenishment potential, not that those gaps are
already closed in an admitted catalogue. Counts may fall at the usability gate.
All baseline selected targets survive these combined trials; additional selected
targets are **pair/level memberships**, not newly discovered global vocabulary.
MKQA's larger numbers cannot replace its unanswered content-fit decision.

## Reproduce without a long workflow

Place the pinned corpus files together in a new local `corpus-review-inputs`
directory (standard-library HTTPS downloads from FILES.json, then verify each
size/hash; do not follow moving `main` references). No library installation or
Tatoeba rebuild is required. Run from the repository root; output paths must be
new and outside input directories:

```bash
python3 tools/catalog/deck_budget_impact.py \
  --selection docs/evidence/everyday-round12/EVERYDAY-SELECTION.json.gz \
  --storage docs/evidence/everyday-round16/STORAGE.json --out BUDGET-12000.json

python3 tools/catalog/replenishment_review.py \
  --primary corpus-review-inputs \
  --manifest docs/evidence/everyday-round20/FILES.json \
  --revisions docs/evidence/everyday-round20/REVISIONS.json \
  --ranks docs/evidence/everyday-round20/PL-KO-RANKS.json.gz \
  --baseline docs/evidence/everyday-round20/PL-KO-BASELINE.jsonl.gz \
  --expected docs/evidence/everyday-round12/EVERYDAY-SELECTION.json.gz \
  --out REPLENISHMENT.json
```

## Verification and next existing-plan step

Local segmentation, v2 schema/ingestion/morphology/builder/parity, census/shards,
selection/admitted Everyday/quality/readiness and both new diagnostic suites
passed. Japanese analyzer runtime is explicitly skipped in the quality suite;
this round's experiment is Polish/Korean. Changed workflow YAML parses, defaults
agree and early-budget steps exist. Text/link/source-manifest and complete-ZIP
checks are recorded with delivery. No Android/JVM build, real import, all-language
replenishment selection or Actions run is claimed.

Continue the existing preassembly validation/admission step: define and check the
usable WMT subset, preserve source notices and licence boundaries, then measure
the other weak directions. Diagnose the 20 allocation-related thin decks
separately. MKQA needs a defensible question-content subset; NTREX belongs to
Knowledge. Global Part 11 freeze, final Part 12 format/bytes/client acceptance and
Part 17 release gates remain open. Full assembly/publication stay deferred.
