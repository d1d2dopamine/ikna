# Part 9 full World provenance pass

The bounded Global Voices run proved that the record-level gate works on live data:
300 of 400 selected documents were verified and 20,881 en-es aligned rows survived
with both source documents attributed. The remaining Part 9 task before Part 11 is
coverage, not a weaker attribution rule.

For an explicitly selected acquisition, **`catalogue v2 world full provenance`** handles one language pair. The workflow
prepares OPUS native XCES identity once, partitions every referenced document by a
stable SHA-256 hash, and verifies shards in parallel. The default is 32 shards with
2 concurrent page fetches per shard, while the matrix is capped at 16 simultaneous
shard jobs. Do not increase concurrency just to shorten a run; Global Voices is an
external site and the provenance build should remain polite and reproducible.

Each shard restores a GitHub Actions cache keyed by corpus version, language pair,
shard count and `cache_epoch`. Verified document/article mappings and stable
provenance failures are reused on a rerun. Transport failures (`fetch-error:*`) are
not cached, so a rerun requests only those transient cases. Changing the shard
count changes document assignment and therefore intentionally starts a new cache
family. Change `cache_epoch` only when we explicitly want to invalidate old
verification results.

The merge step rejects conflicting duplicate mappings, recomputes verified aligned
row coverage from the authoritative alignment map, and writes
`WORLD-ARTICLE-MANIFEST-FULL.{json,md}`. A verified subset is always fail-closed and
publication-safe, but Part 11 must not use the run as a freeze input until
`completeScan` is `true`. Missing shards, unattempted documents or retryable
transport failures make the final workflow gate fail while still uploading the
available evidence artifact. Inspect saved evidence before deciding to resume;
a failed final gate is not itself a failed merge or permission for a full rerun.

## Existing run and evidence recovery

Read-only review on 2026-10-06 of [run 35009216395](https://github.com/d1d2dopamine/ikna/actions/runs/35009216395)
at commit `e73adaf1e5e321f0d0b08b5d4ef052b840de7ea9` confirms prepare and all 32
shards passed. In [merge job 104559011495](https://github.com/d1d2dopamine/ikna/actions/runs/35009216395/job/104559011495),
manifest merge, attribution, normalization and evidence upload also passed;
**`Require complete provenance scan` failed**. The generic exit-1 annotation
does not expose the report counters.

The artifact API lists no retained artifacts for that run. All 188 cache metadata
rows across both pages contain no `gv-` keys at this checkpoint. The old workflow
retained native/shard evidence for 7 days and final evidence for 14 days, both
elapsed; the metadata does not distinguish expiry from manual removal. The log
download returns HTTP 403 in this anonymous session. Precise retry/missing counts
remain unknown. API observations, step outcomes and response-byte hashes are in
[evidence/world-provenance-run-35009216395.json](evidence/world-provenance-run-35009216395.json).

Recover original locally saved reports/logs/alignment/shards if they exist.
The old final artifact definition omitted alignment/native text and shard/cache
inputs; a final report or normalized candidate preview cannot reconstruct the
full scan. The new merge-review workflow cannot retrieve an absent old bundle.
World remains outside the Part 11 freeze until complete evidence is reviewed.
See [ROUND-0.12-05.md](ROUND-0.12-05.md).

## Retained inputs and offline merge review

Future full runs retain native inputs, shard evidence and the full artifact for
90 days (subject to repository retention policy). The full artifact now includes
the alignment map, both native text files, every shard manifest/report, and copied
stable caches `shards/PROVENANCE-CACHE-SHARD-NNN.jsonl`. Cache service entries can
expire independently; the copied artifact is another recovery source.
`WORLD-PROVENANCE-INPUT-SHA256.txt` pins prepared native bytes. Shard report v3
also binds each report to the raw alignment-map SHA-256.

The manual **`catalogue v2 world merge review`** workflow downloads only the exact
`catalogue-v2-world-full-provenance` artifact from an explicit `source_run_id`,
checks the owner's `alignment_sha256` pin and merges saved shards with the given
`expected_shards`. Missing artifacts/inputs or mismatched pins fail; no fallback
OPUS/article fetch is attempted. Review evidence is uploaded before the unchanged
full-scan gate. No publication or World admission is performed.

For a locally retained compatible full bundle, unpack it into `reports/` and run:

```bash
python3 tools/catalog/globalvoices_manifest_merge.py \
  --alignment-map reports/globalvoices-alignment-map.jsonl.gz \
  --manifest-glob 'reports/shards/article-manifest-shard-*.jsonl' \
  --report-glob 'reports/shards/WORLD-ARTICLE-MANIFEST-SHARD-*.json' \
  --expected-shards 32 \
  --out review/article-manifest.jsonl \
  --json review/WORLD-ARTICLE-MANIFEST-FULL.json \
  --markdown review/WORLD-ARTICLE-MANIFEST-FULL.md
```

Use the saved shard count and independently verify the alignment hash first;
`32` matches the earlier run. No network is used by this command. Exit 0 means the
merge executed, **not** that `completeScan` passed. Inspect that field separately.

Merged report v2 retains SHA-256 hashes of map/manifests/reports and full
`recovery.retryableDocuments`, `recovery.unattemptedDocuments` and
`recovery.retryShardIndexes`, without diagnostic preview caps. Stable provenance
rejections stay excluded and do not request transport retries. Missing shards,
skipped documents and transport failures still prevent a complete scan.
Index/partition/outcome/inventory conflicts, foreign documents, changed map hashes,
unsafe report flags and corrupt UTF-8 fail closed. CLI outputs cannot overwrite
input evidence or each other.

Legacy shard report v2 remains readable, with absent input hashes counted in
`inputEvidence.legacyShardReportsWithoutInputHash`. A successful legacy merge
does not manufacture hashes for the old generation step or bypass freeze review.

The recovery plan identifies affected shards; it does not automatically fetch
anything. Before selective continuation, verify saved native inputs, shard count,
pair, corpus version and cache epoch. Preserve verified/stable outcomes and review
outstanding work before recomputing the merge. Changed partitions or unavailable
caches require a separately chosen acquisition, not a hidden full rerun. The
acquisition workflow still schedules its configured matrix; this round adds
offline review, not a selective-fetch workflow.
