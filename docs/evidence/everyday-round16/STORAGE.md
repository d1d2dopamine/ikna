# Selected Everyday storage measurement

Exact accepted selected intermediate; not final packs, installed DB sizes or publication approval.
Every layout is reread from disk and restores every row, field and context order.

| Layout | Total MiB | Largest cold download MiB | Largest cold logical MiB |
| --- | ---: | ---: | ---: |
| Self-contained | 237.403 | 3.005 | 15.906 |
| pair | 194.098 | 3.540 | 19.142 |
| language | 182.881 | 15.472 | 66.725 |

Pool cold logical bytes include the whole dependency plus the deck references;
they are not the reconstructed deck size or measured RAM. Unknown fields and
origin/meaning associations are retained. Language pooling remains diagnostic.

Verified in each layout: 928,162 memberships, 2,094,192 contexts, 2,094,728 origins.

## Decision

Pair pooling has at least 10% intermediate savings and passes exploratory transfer thresholds; final pack/client evidence is still required.

The inherited 220 MiB/24 MiB thresholds are exploratory here: Everyday only,
without final token/credit/enrichment payloads. No global release gate is closed.

## Reader boundary

The selected intermediate is missing required PackChunk fields. Its contexts list
includes the primary; PackChunk.contexts means alternatives only. Do not feed these
experimental files to the existing importer. A pinned no-reselection materializer
needs full function-word rank evidence, canonical-token UTF-16 offsets, tokens,
compatibility source credits and a lossless provenance handoff before client acceptance.
