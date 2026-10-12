# Final independent integration review

Reviewed 2026-10-12 UTC. No blocking issue found in the actual scoped integration.

## Source and application invariants

Compared all 885 files in the Round 33 rollback ZIP against normal source before the Round 34 documentation update. The only changed archived file was `tools/catalog/selection_policy.py`; none was missing. Its bytes exactly match the accepted full-replay candidate, SHA-256 `72783a30ada3995c9cc4e32f96a4fc5ff53940756ceb5895c9530ecd9af92edc`. Apart from `choose_contexts`, the policy AST is unchanged.

All 360 archived app/shared/desktop files and `ikna.keystore` remain byte-identical. The three intended test-only files are the only added implementation/test sources. Build caches were excluded; the ignored bundled pack and optional diagnostic pool already predated integration. Default cap remains 12,000; no persistent cache, app/UI, signing, schema or learner-state integration occurred.

The test fixture chooser AST exactly matches the pre-optimization checkpoint. Tests load it independently rather than aliasing production. Independently ran the 17 durable regressions on actual integrated source with pinned ICU 74.2: all passed in 2.678 seconds. Also inspected successful 143-test and explicit Japanese 13-test logs; these are overlapping suites, not 156 unique tests.

## Corrected normal-source smoke

The first command omitted required `--input-scope` and failed during argparse, exit 2, before selection. Its evidence remains retained. Corrected smoke-02 completed with guard exit 0 and default cap 12,000 on the same 105,988-row diagnostic input as Round 27.

Independently hashed both old/current selected gzip and preview gzip: bytes match exactly. Selected compressed SHA-256 is `2efae253c630285c7b7de5f9de5e076117c39981775cea181e6a7ef51bdc16f6`. Complete reports differ only at the expected policy fingerprint; counts and summary match. Selection elapsed was 21.281 seconds; guard sampled peak RSS was 313,241,600 bytes. Reviewed `SMOKE-COMPARISON.json`, which additionally records independent complete gzip CRC/logical hash verification.

## Evidence scope

Fact-checked selector claims and numerical results in `ROUND-0.12-34.md` and the Round 34 plan entry against finalized evidence; no discrepancy found. The 48.55% reduction belongs to one full historical-cap-8,000 comparison, not the bounded smoke or a guaranteed cross-workload gain. No full cap-12,000 corpus replay, semantic-quality certification or publication claim is established.

No source/producer files were edited by this reviewer. The old strict full-comparison helper was not rerun against the intentionally updated normal source. This report is the only new review artifact created for this final request.
