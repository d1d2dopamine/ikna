# 0.11 Parts 5-10 evidence run

Run these workflows after one commit containing Parts 5-10. None of them publishes
Catalogue assets.

## Order

1. **catalogue v2 supply census** — Part 5. Measures Tatoeba and every direct
   WikiMatrix pair before/after the historical cap.
2. **catalogue v2 everyday experiment** — Parts 6, 7 and 10. Measures MASSIVE
   against Tatoeba, builds the merged Everyday preview, then runs deterministic
   target/context selection.
3. **catalogue v2 knowledge experiment** — Parts 8 and 10. Acquires every direct
   WikiMatrix pair, applies the per-pair review/accept/reject policy, and runs the
   same deterministic selection over the retained Knowledge preview.
4. **catalogue v2 world attribution experiment** — Part 9. Starts with `en`/`es`
   by default, verifies OPUS document identity, and reports which documents still
   lack article-level attribution. Without an article manifest this is expected to
   report unresolved documents rather than invent URLs or contributors.

Use the workflow defaults for the first evidence run. In particular, do not raise
WikiMatrix row limits until the first reports show which pairs are still only lower
bounds.

The two WikiMatrix-heavy workflows (supply census and knowledge experiment) should
not be started at the same time. Run them sequentially to avoid unnecessary load on
the upstream bucket.


## After the first full evidence run

The first Part 5 census ran successfully, but its report review exposed a
lower-bound classification bug: acquisition-capped WikiMatrix rows could be
reported as `deck-cap-truncated` instead of `source-scan-lower-bound`. The first
run hit the 50k acquisition limit for all 55 physical WikiMatrix pairs. The first
200k rerun then stopped at `de-ko` after 82,280 retained source rows because the
score floor intentionally closed the streaming pipe before `max_rows`, while the
old verifier only recognized max-row closes. The workflow now records the reader
stop reason and accepts verified score-threshold closes without accepting a plain
short EOF after an upstream failure. Rerun **supply census** at 200k; any pair that
reaches that bound is still unresolved and must remain a lower bound. Only close
Part 5 once the evidence distinguishes source supply from acquisition truncation.
Then rerun **Everyday**, **Knowledge**, and **World** as needed; those three are
independent and may run at the same time.

- Everyday now uploads only compact reports, manual MASSIVE samples, and a bounded
  selection preview. The multi-hundred-megabyte merged candidate pool stays on the
  runner and is not uploaded as evidence.
- Knowledge uses provisional pair-specific review floors derived from the first
  50k-row diagnostic: `1.10`, `1.11`, or `1.12`. These floors only filter the next
  review pool; every pair still has action `review` until retained samples are
  inspected.
- World resolves XCES document + sentence ids directly against native OPUS XML.
  Moses line numbers are no longer used for provenance. Article URL/contributor
  attribution remains fail-closed and requires trustworthy document metadata.
- Part 10 precomputes target choices once per exact context, bulk-loads SQLite
  before creating the pair index, and keeps only a small evidence preview per deck.
  Selection ordering and deck decisions are unchanged.

## Do not run yet

- Do not enable `publish` in **catalogue v2 build**.
- Do not treat a MASSIVE preview as admitted production material.
- Do not change `catalogue-v2-wikimatrix-quality.json` from `review` based only on
  counts; inspect the deterministic samples first.
- Do not run the final storage/package decision yet. Part 12 belongs after the
  Part 11 content freeze.

## Evidence to bring back

From the workflow artifacts/workflow summaries, keep:

- `CATALOGUE-V2-SUPPLY.*` and the WikiMatrix inventory;
- `EVERYDAY-SOURCE-ADMISSION.*`, `EVERYDAY-MASSIVE-SAMPLES.md`, and
  `EVERYDAY-SELECTION.*`;
- `KNOWLEDGE-PREVIEW.*`, `KNOWLEDGE-SAMPLES.md`, and `KNOWLEDGE-SELECTION.*`;
- `WORLD-NATIVE-XCES.*`, `WORLD-NATIVE-SAMPLES.md`, and `WORLD-ATTRIBUTION.*` plus the missing-document list.

Those reports are the input to the next review. They determine corpus admission,
pair-specific WikiMatrix thresholds/rejections, remaining World attribution work,
and whether the Part 10 policy needs adjustment before Part 11.
