# Round 42 — bounded Polish/Korean source discovery

2026-10-12. Baseline: the complete corpus-free Round 41 ZIP, SHA-256
`918d0f3a9117e31a0a5373ec5d6a07fe29b3443b67efa059354345dc0f6c897b`.
The owner approved a small automated discovery experiment, not automatic source
admission. The owner explicitly forbids AI-generated or rewritten card text,
examples and translations, including drafts for human review.

## Actual scope and outcome

The executable `tools/catalog/corpus_discovery.py` reads the six exact historical
pl/ko gap cells, then requests three fixed public metadata endpoints: OPUS latest
Moses pair metadata, one HF name search, and one first-page HF language/tag search.
It does not paginate, follow redirects, authenticate, fetch corpus archives or
request dataset rows. Each response is bounded to 2 MiB and 500 records. The
30-second timeout is a socket timeout; the executed command additionally uses
`timeout 120` and one-CPU affinity. No paid API is used.

The live requests returned 22 OPUS pair-archive records and 30 HF repository
records; the name query returned zero. These are **52 metadata hits, not 52
matching human-translated corpora**. Twenty-nine names have no existing family
hint in the tool; this does not establish twenty-nine genuinely novel sources.
HF's next page was deliberately not fetched. Broad repository language tags do
not prove a direct Polish/Korean pair, human translation or compatible rights.

Three leads received bounded primary-source follow-up:

- **tldr-pages:** pages have a primary CC BY 4.0 grant, but contribution guidance
  permits machine-translation assistance. Human-only ancestry of a relevant
  subset was not established. It is technical documentation, not demonstrated
  Everyday supply. This family was already in an old raw OPUS inventory; it is
  newly triaged, not claimed as a new corpus discovery.
- **UDHR:** independently published translations of one declaration may provide
  a small World/Knowledge supplement. The specific Polish/Korean translation
  credits and original-text reuse grant remain unresolved. Unicode packaging
  terms alone do not clear the original translations. The OHCHR project page
  returned 403; no bypass was attempted. Sentence alignment, level distribution
  and sufficient independent contexts were not measured. This narrow document
  cannot be promised as an Everyday shortage solution.
- **Apple HAT:** the publisher identifies neural-MT outputs annotated by human
  reviewers, not human-produced replacement translations. Its configurations
  are English-centric; it is not admitted as pl/ko human parallel supply. This
  conclusion uses actual ancestry, not a blanket exclusion of every benchmark.

Two additional obvious metadata false positives are recorded: a repository whose
own description is a model card and a lyric repository described as French
translations despite broad language tags. Dated observations apply only to their
captured revisions; changed revisions remain visible for investigation.

No inspected source established the required human-only ancestry and original
rights for sampling. Therefore corpus downloads, deliberately sampled rows and
admitted cards are all zero. This is a bounded negative result, not proof that
no suitable source exists. Unknown leads remain visible in `LEADS.json`.

## Ranking and family handling

Ranking is transparent investigation priority: explicit pair-archive metadata
gets 30 points; co-present repository language tags get 10; an unmapped family
gets 10. A revision-bound observed scope mismatch subtracts 40. These numbers
are neither semantic-quality scores nor legal-clearance probabilities. Selecting
three primary follow-ups was manual triage, not an automatic quality decision.

Name-based family hints preserve existing reference-only/non-admission/rights
holds without erasing leads. Exact duplicate metadata identities collapse;
versions and named subsets stay visible. Aliases such as `open_subtitles_pl_ko`
and `opus-100-pl-ko` retain family warnings. New provenance may justify a new
review; name matching alone does not prove row-level lineage or duplication.
FLORES/FLEURS and PUD retain their existing reference-only role.

The historical gap report uses an 8,000 ceiling, whereas the current default is
12,000. Neither is used to reinterpret the six measured deficits. The 1,000 thin
boundary is a diagnostic label, not a newly imposed release quota.

## Reproduce without fetching metadata again

From the source root, run:

```sh
python3 -m unittest discover -s tools/catalog -p 'test_corpus_discovery.py' -v
python3 tools/catalog/corpus_discovery.py \
  --cache docs/evidence/discovery-round42/metadata-replay \
  --out /tmp/ikna-discovery-new-replay --replay
```

The output directory must be new. The bundled replay strips HF descriptions,
cardData and examples, retaining identity/revision/tags only. Its transformation
and both original and sanitized hashes are recorded. Exact `LEADS.json` and
`GAPS.json` equality is checked against the final live run. Raw `full=true`
metadata may contain embedded examples; it remains outside the source ZIP.

A live run uses a new external cache, never the project evidence directory:

```sh
timeout 120 python3 tools/catalog/corpus_discovery.py \
  --cache /tmp/ikna-discovery-new-cache --out /tmp/ikna-discovery-new-output
```

This does not fetch any corpus. Future corpus acquisition, if justified, remains
an explicit, bounded workflow/input step; no data payload goes in source ZIPs.

## Verification and limitations

Focused tests cover pair/tag distinction, unknown-lead visibility, family aliases,
revision/duplicate handling, revision-scoped demotion, no content-field emission,
redirect rejection, response bounds, schema failures, output safety and replay.
The unrelated WMT provenance baseline tests are retained. Initial live-run
wording and redirect/ranking defects were corrected after independent review;
`INITIAL-RUN-ERRATA.json` preserves that distinction. Do not use the initial run
as evidence for final code.

Source/app assets, user-facing UI, catalogue formats, source admission, learning
policy and signing identity remain unchanged. No Android/Windows GUI claim or
application-build claim is made for this metadata-tool stage.

## Next decision

The best new visible lead, UDHR, first needs source-specific Polish/Korean rights
and translator evidence. Even after clearance, its domain and small scope require
an actual yield check; it is not a replacement for the missing Everyday supply.
Other leads can be investigated in a separately bounded round if selected. This
run stops without a broad crawler, automatic corpus expansion or generated text.
