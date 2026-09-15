# Part 9 full World provenance pass

The bounded Global Voices run proved that the record-level gate works on live data:
300 of 400 selected documents were verified and 20,881 en-es aligned rows survived
with both source documents attributed. The remaining Part 9 task before Part 11 is
coverage, not a weaker attribution rule.

Run **`catalogue v2 world full provenance`** for one language pair. The workflow
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
available evidence artifact. Rerun the same workflow inputs to resume from cache.
