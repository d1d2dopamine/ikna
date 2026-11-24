# 0.12 corpus replenishment candidates

Initial research: 2026-10-08; acquired review: 2026-10-09, Asia/Yekaterinburg.
This document owns the shortlist and admission decisions after the
[Everyday shortage diagnosis](ROUND-0.12-12.md). [Round 20](ROUND-0.12-20.md)
acquires pinned files and measures Polish/Korean replenishment. It does not admit
production sources or alter the accepted control snapshot. Initial findings
below remain dated evidence; the current decision is in the round-21 section.

## Initial recommendation — 2026-10-08

Evaluate **WMT24++ first**, using its human-reference social material and suitable
short speech segments. Evaluate **MKQA query translations second**, restricted to
complete, useful questions that genuinely fit Everyday. Both offer shared human
translations in all eleven ikna languages, including Polish and Korean. This is
a recommendation to measure, not a promise that they fill every pair/level.

For broader non-Everyday material, retain **NTREX-128** as a separate Knowledge
candidate. Keep PUD and FLORES/FLEURS as reference material for now. No large,
ready-to-publish conversational corpus meeting every project constraint was
established by this search.

## WMT24++ — priority 1, bounded Everyday experiment

Publisher: Google. Primary references:

- [Official data card and schema](https://huggingface.co/datasets/google/wmt24pp/blob/main/README.md).
- [Authors' paper](https://arxiv.org/html/2502.12404v1), sections 2.1–2.2 and Table 2.

Verified upstream facts: 998 shared English source **paragraphs**, translated into
55 target language/locale variants. References and subsequent edits were produced
by professional translators. Domains include 531 social, 111 speech, 206 literary
and 149 news paragraphs, plus one canary. The official card declares Apache-2.0;
rows have document/segment IDs, domain, bad-source flag, original reference and
edited reference. It separates human references from MT/LLM system outputs.

Proposed measurement: start with social and qualifying speech text; exclude canary,
bad-source rows and machine system hypotheses. Use a consistent human-reference
variant. Verify identical source/document/segment anchors before pairing Polish
and Korean translations; this joins existing human translations, not generated
translation through English. Whole aligned segments can pass the existing length
gate. Do not split both translations by punctuation and zip the results: sentence
boundaries need not match. Keep paragraph evidence or exclude unresolved alignment.

Recommendation limits: domain labels are not pedagogical approval. Some social
text is informal or entity-heavy; speech paragraphs can exceed card limits. Check
source-text redistribution and required notices against the declared licence.
The small segment base cannot establish thousands of new cards per level without
an actual yield test. Keep all rejected originals and reasons; no text rewriting.

Language mapping proposed for inspection: English source; `de_DE`, `es_MX`,
`fr_FR`, `it_IT`, `ja_JP`, `ko_KR`, `pl_PL`, `pt_PT`, `ru_RU`, `zh_CN`. Spanish
and Portuguese locale choices need an explicit product decision; do not mix locale
variants or count them as distinct learning targets to inflate supply.

## MKQA — priority 2, question-only Everyday experiment

Publisher: Apple. Primary references:

- [Official repository, dataset/schema/licence sections](https://github.com/apple-aiml-research/ml-mkqa).
- [Authors' paper](https://aclanthology.org/2021.tacl-1.82.pdf).
- [Publisher dataset mirror](https://huggingface.co/datasets/apple/mkqa/blob/main/README.md).

Verified upstream facts: 10,000 Natural Questions queries with human translations
into 25 additional languages/variants. All eleven ikna languages are represented;
`example_id` anchors corresponding queries. Dataset terms in the official README
are **CC BY-SA 3.0**, while repository code is Apache-2.0. The mirror's CC BY 3.0
metadata disagrees with the primary dataset terms; use the primary terms as the
conservative research baseline and retain the discrepancy for admission review.

Proposed measurement: pair only existing `queries[lang]` and `queries[meaningLang]`
under the same example ID. A question's translation is its meaning; the QA answer
is not a translation. Exclude answers, aliases, model predictions and Wikipedia
passages. Reject telegraphic/fragmentary queries, quoted lyrics and entity/trivia
dominance; measure the useful general-question subset rather than importing all
queries. Do not rewrite searches into grammatical sentences. Use `zh_cn` for the
existing simplified-Chinese scope; document other locale choices.

Recommendation limits: question-only material can diversify interrogatives but
cannot supply a balanced Everyday catalogue alone. Net vocabulary/context gains
and semantic correspondence remain unmeasured. Audit ShareAlike and attribution
for the redistributed text and proposed pack layout; do not borrow the software
licence or assume compatibility of a mixed-licence deck.

## NTREX-128 — separate Knowledge candidate

Publisher: Microsoft. References:

- [Official repository](https://github.com/MicrosoftTranslator/NTREX).
- [Dataset licence](https://github.com/MicrosoftTranslator/NTREX/blob/main/LICENSE.md).
- [Authors' paper](https://aclanthology.org/2022.sumeval-1.4.pdf).

The paper describes 1,997 news sentences in 123 documents, translated by humans
into 128 target languages, including the project's languages. The repository
declares CC BY-SA 4.0 and supplies human references plus document boundaries.
This may add directly corresponding non-English text under verified line/document
anchors. It is news material: measure as Knowledge, not as an Everyday gap repair.
If used as production supply later, exclude those records from held-out QA.
Benchmark translation direction is not a guarantee of sentence equivalence or
of ikna card suitability; the normal gates still apply.

## Candidates not recommended for immediate production supply

| Resource | Finding and decision | Primary reference |
| --- | --- | --- |
| PUD, including Polish/Korean | 1,000 corresponding sentences, mostly news/wiki. Korean terms are CC BY-SA 3.0; Polish terms CC BY-SA 4.0. Preserve language-specific licence/translation ancestry. Useful bounded QA; not a broad Everyday replacement. | [Korean](https://universaldependencies.org/treebanks/ko_pud/index.html), [Polish](https://universaldependencies.org/treebanks/pl_pud/index.html) |
| FLEURS / FLORES | FLEURS uses 2,009 n-way parallel FLORES dev/devtest sentences. Do not count the same underlying material as an independent source or bypass the existing FLORES QA-only decision by renaming it. | [Google FLEURS card](https://huggingface.co/datasets/google/fleurs/blob/main/README.md), [existing policy](CORPORA-0.11.md) |
| OpenSubtitles | Attractive conversational domain. The inspected OPUS download page did not establish a licence granting redistribution of every subtitle text. Rights, provenance and alignment quality remain unresolved; not admitted. This is an evidence gap, not a finding that all subtitles are forbidden. | [OPUS source page](https://opus.nlpl.eu/legacy/OpenSubtitles-v2018.php) |
| TED-derived corpora | TED's standard talk reuse terms include NC/ND restrictions. Do not assume an OPUS/TED download permits rearranged public study packs; corpus-specific permission would have to establish a different applicable grant. | [TED usage policy](https://www.ted.com/about/our-organization/our-policies-terms/ted-talks-usage-policy) |
| NIKL Modu parallel corpora | Relevant Korean data exists, but the official FAQ describes approved-purpose use and restrictions on disclosing corpus text. Not a freely redistributable drop-in source for static packs under the current evidence. | [Official corpus list](https://kli.korean.go.kr/corpus/openapi/corpusList.do?lang=en), [official FAQ](https://kli.korean.go.kr/boards/faqList.do?lang=en) |
| MASSIVE / WikiMatrix expansion | Preserve the documented non-admission decisions; a need for more entries does not overturn measured semantic problems. | [Decision record](PARTS-6-9-DECISION-RECORD.md) |

## Proposed acceptance measurement

Use the [round-12 inventory](evidence/everyday-round12/COVERAGE.json) as the baseline:
53 below-threshold decks already lack post-sieve targets; 20 cross the threshold
during distinct-context allocation. Diagnose the latter independently. Neither a
new corpus nor a larger cap is automatically the answer to every loss.

1. Pin exact candidate revisions/file hashes and primary data terms before any
   experiment. Keep source registry admission and publication off. The URLs checked
   here are research references, not reproducible ingestion pins.
2. Establish row identity, human translation provenance and verified multiway joins.
   Start with Polish–Korean, then other weak Korean directions and measured
   Polish/Portuguese/Chinese combinations; report both directions separately.
3. Compare candidates with the accepted Tatoeba selection using stable target IDs.
   Report new eligible targets, new distinct contexts, exact overlap, rejected rows
   by reason, after-allocation yield and below-40/below-1,000 transitions per level.
   Repeated multilingual records do not count as additional source sentences.
4. Keep baseline frequency ranks for the first comparable incremental diagnostic.
   Report out-of-baseline vocabulary separately; do not discard it silently. Any
   later combined-source rank rebuild is a separately labelled measurement, because
   it can move targets between frequency levels and confound an apparent gain.
5. Bound deterministic samples and automatic alignment/shape checks; no exhaustive
   owner/native-language review or giant all-source rebuild as the first step.
   Structural checks and similarity scores do not certify translation truth.
6. Admit only a measured useful subset after the licence/provenance/domain/quality
   decision; design licence-honest packs before mixing sources. Keep the old accepted
   snapshot and give any new admitted generation its own source/build identity.

No numerical yield is promised: 998 paragraphs, 10,000 questions and 1,997 news
sentences are source sizes, not final cards. If the useful subsets do not close a
gap, retain an honest small/absent deck and record the remaining shortage.

## Initial research verification and limits — 2026-10-08

Research used publisher cards/repositories, authors' papers and official terms;
source links and date are retained here. Documented licence distinctions and
unresolved evidence are explicit. No new data download, adaptation, model, workflow,
registry change, app change, census, freeze or publication occurs. Documentation
text/relative-link checks and complete source ZIP verification are the relevant
local checks; application builds are unnecessary for this documentation batch.


## Owner scale scenario — 2026-10-08 follow-up

The owner proposes a future 10,000–12,000 target budget and optional partial
installation with further/full download; **keep today's 8,000 budget unchanged**.
[UNSCHEDULED.md](UNSCHEDULED.md#catalogue-deck-size-and-progressive-download--owner-scenario-2026-10-08)
records alternative packaging routes and acceptance questions. To retain the
existing quality profile while increasing supply, the owner expects that 1–3
additional open corpora may be needed. This is an unmeasured hypothesis, not a
requirement to admit three sources or proof that the current shortlist suffices.
Report incremental useful targets/contexts, per-pair/level coverage, source/domain
balance and real bytes, rather than source count alone. Do not lower quality gates,
add filler or count shared targets as new learner memories to reach a headline.


## Round 20 — acquired licence and yield review

The owner approved a 12,000-target ceiling on 2026-10-09. The earlier scale
scenario's instruction to leave 8,000 unchanged is superseded; progressive
installation remains unscheduled. [Round 20](ROUND-0.12-20.md) and its
[measurement](evidence/everyday-round20/REPLENISHMENT.json) own executed evidence.
No source-registry admission or final content freeze is changed.

| Candidate | Declared data terms and distribution route | Current decision |
| --- | --- | --- |
| WMT24++ | Publisher card Apache-2.0; professional human post-edits only. Carry Apache licence, applicable upstream copyright/NOTICE information, dataset/paper credit and modification notice. Preserve locale, revision and document/segment references. | First Everyday replenishment candidate. Eleven-language anchors agree; 605 whole social/speech segments survive source/domain exclusions. Usable-content subset and production credit handoff still required. |
| MKQA | Primary dataset CC BY-SA 3.0; code Apache-2.0 and mirror CC BY 3.0 are not replacements. Preserve Apple/Longpre–Lu–Daiber credit, dataset title/link, original data terms and extraction/modification description. | Conditional question-subset candidate. Do not admit all 10,000 queries to Everyday merely because their structural yield is high. |
| NTREX-128 | CC BY-SA 4.0. Preserve Federmann–Kocmi–Xin and upstream WMT 2019 credit, terms, modification description, revision, line and document references. | Knowledge candidate only; Polish/Korean shape check completed, net Knowledge yield unmeasured. |

Primary pinned repository/card URLs and exact SHA-256 values are in
[FILES.json](evidence/everyday-round20/FILES.json); dataset revisions are in
[REVISIONS.json](evidence/everyday-round20/REVISIONS.json). Read the publisher's
README data grant separately from its software LICENSE. No inspected primary
grant here introduces NC/ND; this finding does not approve every row's learning
quality or replace required attribution.

Packaging interpretation based on the primary terms: keep original data
licences visible. A conservative route for adapted MKQA/NTREX material is a
separate source asset under its own BY-SA version; do not label it Tatoeba-only or
Apache-only. The [BY-SA 3.0 legal code](https://creativecommons.org/licenses/by-sa/3.0/legalcode)
distinguishes collections from adaptations, while the
[BY-SA 4.0 legal code](https://creativecommons.org/licenses/by-sa/4.0/legalcode.en)
also addresses database rights. That distinction is not a blanket mixed-pack
compatibility approval. Licence/version/credit associations must survive shared
pools, index, import and export. Source separation does not automatically change
the application's code licence. The current one-family deck contract must be
extended deliberately before a real mixed-source deck is emitted.

WMT's declared grant is [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0):
retain required notices and mark changes; it is not ShareAlike. These are
operational packaging requirements inferred from primary terms, not proof of
independently checked rights at every upstream website. Preserve available
source ancestry and do not invent missing author/URL evidence.

Measured potential: adding the bounded WMT subset changes Polish→Korean middle
32→286 and advanced 30→376, above the 40-card inclusion floor; beginner 51→259.
Reverse-direction counts become 222/189/226 for beginner/middle/advanced.
All six baseline counts reproduced first; no baseline selected target was lost.
These are trial memberships with baseline ranks, not admitted final cards.

MKQA's all-query trial gives much larger counts but includes fact/name/entertainment
search questions. Such counts are an optimistic pre-domain diagnostic. A broad
import is rejected as an Everyday solution; retain only a future explicitly
validated useful question subset. Unknown vocabulary is reported separately,
without manufactured ranks. NTREX's 1,997 aligned lines/123 documents establish a
shape input, not net new Knowledge targets.

Next existing-plan step: define/check automatic usability and ambiguity gates for
the bounded WMT subset, design licence-honest provenance handoff and measure
other deficient directions. Do not reopen MASSIVE/WikiMatrix non-admission,
request a fresh Tatoeba export or build the full catalogue for this review.

## Round 21 — larger sources and owner supplement decision

On 2026-10-09 the owner retains **MKQA and WMT24++ for future supplementation
across all eligible decks**, conditional on quality, validity and licence/provenance
checks. The current Polish/Korean trial is a first measurement, not a permanent
pair restriction. This does not approve the entire MKQA query set for Everyday.
The 12,000 ceiling remains in force; final Catalogue assembly remains deferred.

[LARGE-CORPUS-CANDIDATES-0.12.md](LARGE-CORPUS-CANDIDATES-0.12.md) owns the larger
source review: DGT, EUbookshop, Korean Parallel Corpora news-v1, Europarl and
MultiParaCrawl. Each has a collection/pair scope and an explicit rights or quality
gate. Some sources have large relevant counts but incomplete redistribution rights;
being on this review list does not make them ready production sources.

The review separates packaging rights from text rights and website licences from
publication notices. It also records why web-mined millions, subtitle counts,
monolingual terabytes and repeated versions cannot be used as guaranteed new card
yield. The existing shortage/allocation distinction and MASSIVE/WikiMatrix
non-admission decisions remain intact. No registry or publishing flag changes.

Next: bounded, properly scoped source feasibility/rights checks and incremental
measurements from that document, alongside existing preassembly validation. Use
retained metadata first; no fresh Tatoeba export, full rebuild, exhaustive owner
language review or new long workflow is required for this research round.

## Round 22 — processing/evaluation prerequisite recorded

The owner's follow-up discusses long workflows and whether a fast deterministic
classifier would improve content selection. The
[selection-engine plan](SELECTION-ENGINE-PLAN-0.12.md) records profiling, reusable
intermediate data and a bounded comparison before further expensive expansion.
This is a plan, not a classifier adoption or evidence that current rules fail.
Rights/provenance feasibility research remains active, and classifiers cannot
manufacture missing translations or grant text rights. The round-21 candidate
holds, WMT24++/MKQA supplement decision and preassembly validation remain intact.
