# 0.12 round 05 — World evidence recovery, 2026-10-06

Owner authorized Catalogue v2 work after checking round 04. Source baseline:
`ikna-0.12-press-round-04-hot-reload.zip`, 556 files, SHA-256
`98299ea0c1ab3cda7bb1be9e0651a8769890b3c60f00ccb98211349cd730aed1`.
Every source byte matched that archive before this round. Both repository skills
apply. Owner acceptance is reported; no detailed native test matrix is inferred.

## Existing World result, without another acquisition

Read-only anonymous GitHub REST/raw requests inspected the existing
[run 35009216395](https://github.com/d1d2dopamine/ikna/actions/runs/35009216395),
commit `e73adaf1e5e321f0d0b08b5d4ef052b840de7ea9`, from 2026-09-15.
The committed [API checkpoint](evidence/world-provenance-run-35009216395.json)
retains all 34 job outcomes, 13 merge-step outcomes, exact URLs and SHA-256 of
eight retrieved metadata/workflow responses. These are API evidence, not a
reconstructed full provenance report.

| Observation | Consequence |
| --- | --- |
| Prepare and all 32 shard jobs passed | Job success is established; it does not establish `completeShardScan` for every report |
| Manifest merge, attribution, normalization and evidence upload passed | The previously described “merge failed” means the merge **job** failed; the merge algorithm itself succeeded |
| `Require complete provenance scan` failed; annotation only says exit 1 | Preserve the completeness gate. Actual missing/retry/unattempted counts remain unknown |
| Artifact API lists zero retained artifacts | No usable old artifact download is available in this checkpoint; old inputs/shards were retained 7 days, final evidence 14 days, both elapsed |
| All 188 cache metadata rows, across two pages, have no `gv-` keys | Current service metadata offers no saved Global Voices cache family; do not assume a rerun will reuse that old scan |
| Merge log download returns HTTP 403 | Log details require an authorized reader or an originally saved local copy; no credentials/session were probed |

The retention periods are confirmed from the workflow at the exact run commit.
Removal cause is not exposed: expiry and manual removal cannot be distinguished.
The dispatch inputs and native/alignment hash are also unavailable. Do not invent
precise transport failures from the stage result. Part 9 remains open.

## Implemented recovery and integrity repairs

The old final artifact did not include all inputs needed to reproduce the merge
and attribution: alignment map, native text, shard reports/manifests and stable
caches were separate or only cached. Combined with short retention this prevented
later review/reuse. Future full runs retain those inputs together for 90 days,
with a prepared-native hash file. Stable caches are copied into ordinary shard
artifact paths so artifact retention is independent of the cache service.

New shard reports (v3) contain `alignmentMapSha256`. The merger validates matching
input hashes, World report version/part/safety flags, legal shard indexes,
unresolved-document partition, inventory membership, affected-row counts and
conflicting outcomes. Unknown verified documents and corrupt UTF-8 fail instead
of being silently included/repaired. CLI output aliases cannot overwrite evidence.
Identical verified duplicates are still deterministically deduplicated.

Merged report v2 records hashes of every alignment/manifest/report input and adds
uncapped recovery lists: missing shard indexes, all unattempted documents, all
retryable documents and the union of affected shard indexes. Existing bounded
display samples remain for readability. Stable provenance rejections remain
excluded without requesting transport retries. Completeness is still independently
computed from the authoritative inventory; no cap, invented credit or weaker gate.
Legacy shard v2 input is readable and explicitly counted as lacking generation
hash evidence. It does not acquire a retroactive provenance proof.

The manual `catalogue v2 world merge review` workflow takes explicit saved run,
alignment hash and shard-count pins. It downloads only the fixed full-evidence
artifact, performs an offline merge, uploads review evidence and enforces the full
scan gate. Missing/expired input does not trigger a fresh OPUS/article fetch.
It cannot recover this old run's now-absent bundle. It is not an automatic
selective-fetch/resume workflow, nor a World admission/publication path.

The owning commands, formats and recovery decision are in
[PART-9-FULL-PROVENANCE.md](PART-9-FULL-PROVENANCE.md). No full workflow was
dispatched or retried, no external corpus was downloaded and no public catalogue
asset was modified. Prior source-admission decisions remain unchanged.

## Verification

- `python tools/catalog/test_globalvoices_manifest_merge.py`: 19 offline tests,
  all pass. Covers deterministic complete/partial merges, transport versus stable
  rejection, missing shards, uncapped 598-document recovery, mixed/new/legacy
  hashes, invalid indexes/flags, inventory/partition/outcome conflicts, duplicate
  identity, row counts, UTF-8 and input/output alias protection.
- Existing `test_globalvoices_attribution.py` and `test_ingestion.py` pass,
  retaining native-XCES/attribution/normalization contracts.
- Both World workflow YAML files parse; 16 shell blocks pass `bash -n`, six
  embedded Python blocks parse. Seven executed reuse-pin cases cover valid input,
  absent/malformed run/hash/count and injection-like strings.
- An executed local saved-artifact fixture rejects missing/hash-mismatched
  alignment input, reproduces a complete merge and gate pass, and retains a
  transient-error report while the gate fails. Alignment bytes stay unchanged.
  No network was used by those fixture stages. This is not an Actions transfer
  test or a full-corpus run.
- Text/localization, scoped byte/link review, `git diff --check` and complete ZIP
  manifest/byte/integrity checks apply before delivery. New regression tests are
  included in grading and the full World workflow's pre-acquisition contracts.

No application/JVM/Android build or Windows UI pass was needed/performed for this
Catalogue Python/workflow change. The actual historical full report is absent,
so precise World counters, real input replay and Part 9 completion remain open.

## Parallel GLM lane and delivery

The owner already has a separate GLM prompt and the common round-04 ZIP. GLM owns
Windows window transition artifacts, narrow Browse and focus/keyboard return;
its report will be `ROUND-0.12-05-GLM.md`. This Catalogue lane leaves all desktop,
shared UI, Hot Reload tooling, skills, schemas, signing/binary assets and build
versions byte-identical to that common base at Catalogue-lane delivery. GLM's
subsequent documentation-only output has now been reviewed and integrated in
[ROUND-0.12-06.md](ROUND-0.12-06.md); its unproven visual conclusions were rejected.

Changed existing source: both Global Voices manifest scripts, the full World and
grading workflows, CHANGELOG, CATALOGUE-V2, full-provenance runbook, documentation
map and the active/application plans. Explicit additions: the merge-review
workflow, `test_globalvoices_manifest_merge.py`, this report and the JSON API
checkpoint. Full source package: 560 files, no deletions; no local caches/corpora,
raw API dumps or build outputs enter the manifest.

Next evidence checkpoint: recover original locally saved World reports/native
inputs/shards or a readable merge log if available. Choose outstanding work from
the actual report rather than launching another known full scan. Full admitted
Everyday input/material review remains the next Catalogue dependency. Review and
integration with GLM is now recorded in round 06; Browse/focus acceptance remains
open because the delivered report contains no interactive evidence.
