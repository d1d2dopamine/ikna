# External corpus evidence in source-only checkpoints

From Round35, source ZIPs contain code/configuration/tests and compact reports,
not raw corpora, real sampled cards or corpus-derived word lists. The original
owner-supplied archive is preserved; no local historical input was erased.
`SOURCE-PACKAGE-OMISSIONS.json` records every excluded path and its original hash.
Missing historical inputs are intentional, not a successful replay condition.

## New WMT metadata reconstruction

The manual `catalogue-v2-wmt-provenance-review.yml` obtains only the two official
pinned WMT24++ language files described by Round20 FILES.json. Raw files live in
runner temporary storage. Only WMT-ANCHORS.json is retained. Its local CLI accepts
`--primary` pointing at those external files, verifies both hashes, and fails
closed on changed bytes, source code or aggregate counts. No previous sampled
Tatoeba evidence is needed for this new path. No corpus text appears in its output.

## Historical selection/profile and replenishment replays

Old reports are hash-bound evidence of their original run. Removing embedded
sentence samples would change their hashes, so this package omits those originals
rather than silently rewriting them as if unchanged. Counts remain in the retained
selection summaries, census/coverage Markdown and Round35 CSV/metadata. A detached
summary must never be substituted for an original input expected by a hash gate.

- `replenishment_review.py` already takes explicit external `--primary`, `--ranks`
  and `--baseline` paths. Round20 BASELINE-PASSPORT.json records the exact baseline
  and rank hashes, input pool identity and rank procedure. To repeat that complete
  yield trial, first obtain its exact retained external evidence; it is not made
  self-contained by this source ZIP. New official source revisions do not reproduce
  a historical hash-bound trial automatically.
- `selection_diagnostic_pool.py` generates diagnostic-pool.jsonl.gz from an explicit
  full admitted input and its pins. Keep its generated payload external. Full pool
  references and hashes are retained in the associated reports/runbooks. Do not
  relabel a bounded sample as the full corpus.
- `catalogue-v2-everyday-pool.yml` defaults to exact artifact reuse. Fresh Tatoeba
  acquisition is a separate explicit mode and creates a new input identity.
  Artifact availability/retention must be checked before attempting reuse. This
  checkpoint does not claim old Actions artifacts still exist.
- `catalogue-v2-everyday-quality.yml` retrieves the original full pool and baseline
  artifact in Actions and verifies exact pins. It does not consume a stripped
  version of those reports from this source ZIP.
- Old rendered card viewers and manual-review samples are historical aids, not
  runtime assets or a prerequisite for new metadata extraction. Their old document
  links may point to intentionally omitted external evidence; the omissions
  manifest explains this rather than fabricating replacement content.

Synthetic unit fixtures, authored regression cases, policies, source metadata,
application resources and signing identity remain in the source package. The
omissions list is explicit; no generic extension-based deletion is used.
