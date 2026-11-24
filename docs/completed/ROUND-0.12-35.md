# Round 35 — coverage diagnosis and metadata provenance recovery

2026-10-12. The owner requests full Catalogue coverage together with quality,
with corpus acquisition in GitHub Actions and no corpus payloads in source ZIPs.
This checkpoint does not change the selector, application/UI, source registry,
licence admission, catalogue content or publication status.

## Coverage recovered from existing evidence

The supplied Round34 ZIP was verified before reading. The complete Round30 and
Round33 deck/material report fields agree. The report-only diagnosis retains all
330 Everyday pair/level rows and the exact 73 below the historical thin boundary:
71 thin and two omitted. All 73 have zero budget rejections. Of them, 53 already
have fewer than 1,000 eligible targets and 20 cross that boundary during context
allocation. Forty-eight involve Korean; 27 directions have at least one weak
level. The two omissions are Polish learned with Korean meanings: middle 32,
advanced 30; beginner retains 51. These are historical cap 8000 observations, not
a new default 12000 census or a complete v2 acceptance decision.

The diagnostic distance to 1,000 is 39,269 pre-omission memberships, or 39,331
relative to included output. Neither is a new acceptance quota or a count of
unique global words. Existing frequency bands limit beginner to ranks 61–1500
and middle to 1501–5000. The 12,000 safety maximum cannot be a promise of 12,000
unique targets in every level. The current full-coverage requirement needs an
explicit useful-volume profile; no smaller-release compromise is inferred.

Knowledge and World remain separate. The all-direct Knowledge count is not
semantic admission, and the missing World completeness report cannot be replaced
with invented counters. The old bounded Round 15 allocation witness is not rerun
or extrapolated to the full input.

## Targeted Tatoeba link check

The official Polish/Korean direct-link export dated 2026-10-10 was read as a
565-byte metadata file. Its 94 unique edges exactly match all 94 edges in the
saved 188-row directed Round 20 baseline. No sentence text was fetched for this
check. There is no measured new link supply from this newer snapshot for this
pair. Existing sentence edits, current sentence licences and quality were not
rechecked; other language pairs are outside this observation.

## WMT provenance reconstruction

Round 20 already measured a conditional WMT24++ social/speech trial. Repeating its
count pilot would not resolve the missing per-row provenance. The new
`wmt_provenance_manifest.py` reconstructs original anchor decisions from the two
exact pinned Polish/Korean inputs, checks agreement with the old aggregate and
outputs IDs, domains, reasons and text hashes without source/reference text.
It does not perform target selection, semantic scoring or source admission.
Rights remain explicitly unresolved. The Apache dataset card does not by itself
resolve every original WMT24 text's terms. The official companion document-to-URL
metadata is the next bounded audit source, not an ingestion passport.

The manual-only `catalogue-v2-wmt-provenance-review.yml` fetches only those two
pinned files into runner temporary storage, verifies sizes/SHA256, runs the tool
and retains only the metadata JSON. It has `contents: read`, no publishing step,
and no raw-input artifact upload. No external commit or workflow dispatch was
performed in this checkpoint.

## Delivery and rollback

The original owner-supplied ZIP remains unchanged. Future source packaging omits
corpus payloads and embedded historical sentence samples by an explicit manifest;
it does not rewrite their original hashes or pretend that they are still present.
Actual application assets and synthetic test fixtures remain source assets.
Older diagnostic CLIs accept external corpus paths: restore the exact pinned
inputs through the documented workflow/input contract before historical replay.
Do not substitute stripped metadata projections where an original hash-bound
report is required.

Rollback of the new diagnostic behavior removes only the new tool, its tests and
its dedicated workflow. Existing selection and replenishment policies are intact.
Verification results and package omissions are recorded alongside this checkpoint.

Primary rights-audit leads: [WMT24 licensing](https://www2.statmt.org/wmt24/translation-task.html), [official WMT24++ card](https://huggingface.co/datasets/google/wmt24pp), and [companion document/source metadata](https://huggingface.co/datasets/google/wmt24pp-images).

## Verified execution

The two original pinned files were read from their official immutable URLs solely
for local diagnostic validation (1,223,239 and 934,176 bytes, 2,157,415 total). Both
matched the stored SHA256 and size before parsing. The durable collection route
is the new Actions workflow; no corpus is delivered in this source package.

Twenty-two synthetic tests passed, including 384 scalar decisions and a 300-row
shuffled comparison against the unchanged original `wmt_join`. The real metadata
run reconstructed 998 anchors: 605 retained (494 social, 111 speech), 38 bad-source and 355
outside-domain. All aggregate counts and rejection reasons match Round20.
This validates only the Polish/Korean inputs and their shared English anchors;
it does not claim a new all-eleven-language check or repeat the target-yield trial.

The resulting text-free `WMT-ANCHORS.json` is 476,351 bytes, SHA256
`31c90e3a906c9aac9aa3701e7d49a5ad6e14f7b6817b51673cc7d0e44c0df504`.
The per-document rights audit can now use exact retained IDs. YAML and embedded
Python parse checks passed; workflow dispatch remains unperformed.

## Original-source mapping outcome

The separate bounded source audit maps every retained anchor to 145 original
documents (34 social documents covering 494 anchors; 111 speech documents). Seven
publisher-marked unavailable documents affect 57 anchors. Five original-page checks
established no compatible original-work licence. This is zero positive rights
evidence in the performed checks, not a claim that every work is prohibited.
All 145 document rights statuses remain unresolved. Every speech source URL also
contains a corpus chunk suffix in its YouTube video parameter and needs canonical
identity verification before using it as attribution evidence.

The distilled `WMT-SOURCE-RIGHTS-METADATA.json` binds this anchor manifest and
records URL/status/response hashes only. The companion rows API does not expose
a dataset revision: the observed repository commit is recorded separately and is
not claimed to bind the returned rows immutably. Raw API pages with image asset
URLs and corpus payloads are not delivered. WMT remains conditional; no rights
or source-quality gate was cleared by the successful 605-anchor reconstruction.
