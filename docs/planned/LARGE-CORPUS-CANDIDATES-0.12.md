# Large corpus shortlist for Catalogue v2

Research date: 2026-10-09. This is a **review shortlist, not source admission**.
It supplements [the acquired small-corpus review](CORPUS-CANDIDATES-0.12.md)
and [Round 21](ROUND-0.12-21.md). No corpus import, final assembly or publication
is authorized by this document.

## What needs supply

The saved [shortage inventory](evidence/everyday-round12/COVERAGE.json) has
73 Everyday decks below 1,000: 53 lack targets before context allocation and
20 lose candidates during allocation. 48 involve Korean. Polish→Korean middle
and advanced are omitted at 32/30; the inclusion minimum remains 40. Other weak
directions include Polish–Portuguese, Italian–Polish and Polish–Chinese.
The approved 12,000 ceiling does not cure any of those 73 shortages by itself.

The owner retains **MKQA and WMT24++ as future supplements across all eligible
decks**, subject to quality, validity and licence/provenance gates. Round 20's
Polish/Korean measurements do not restrict future measurement to that pair.
Neither source is a balanced, large replacement corpus or admitted production
content. NTREX remains a separate non-Everyday review.

## Selected candidates and measured scope

Counts below are upstream **alignment records**, not unique sentences, new
targets, useful cards or incremental yield. OPUS versions are not additive.
Metadata was obtained from the official API and retained in
[RESEARCH.json](evidence/corpus-round21/RESEARCH.json), with exact responses and
SHA-256. No full candidate corpus was acquired. Unreported counts stay unknown.

| Candidate/version | Relevant upstream scale | Proposed scope | Current gate |
| --- | --- | --- | --- |
| DGT / OPUS v2021 | pl–pt 5,543,617; it–pl 5,514,594 | European-pair Knowledge suitability experiment | Dataset-specific reuse grant found; domain and usable yield open |
| EUbookshop / OPUS v2 | pl–pt 413,144 | Neutral explanatory Knowledge subset | Publication-specific copyright notices must survive extraction |
| Korean Parallel Corpora / news-v1 | Reported train 94,123; dev 1,000; test 2,000 | ko–en World/news; reserve evaluation splits | Declared BY-SA 3.0; upstream text rights and row ancestry unresolved |
| Europarl / OPUS v8 | pl–pt 608,181 | World/social-policy speech; explanatory subset only if justified | Extract/adaptation rights unresolved; no blanket CC licence established |
| MultiParaCrawl / OPUS v9b | pl–ko 250,888; pl–pt 7,551,962; further Korean pairs below | Potential rights-cleared web subset for deficient pairs | Hold: packaging licence does not grant underlying text rights; pivot alignment unvalidated |

These roles are proposals, not collection admissions. A legal or parliamentary
sentence does not become Everyday because it contains a common word. Do not
fill Everyday gaps by relabelling Knowledge/World material. No reviewed large
source here is established to close both omitted Everyday levels in production.

## DGT: first large European-pair experiment

Primary [JRC dataset page](https://joint-research-centre.ec.europa.eu/language-technology-resources/dgt-translation-memory_en)
describes human translations in 24 EU languages. Translation units may be partial
sentences; later alignments are automatic. English is the extraction anchor,
not a newly generated translation. Preserve TMX unit/document identities.

Its dataset grant invokes [Decision 2011/833/EU](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32011D0833):
reuse includes commercial purposes, with source/website/update-date and Commission
ownership credit, no misleading meaning and applicable third-party exclusions.
**EUPL covers extraction software, not the text database.** Do not replace these
dataset-specific terms with a generic website licence.

Recommendation: review a bounded pl–pt / it–pl sample for complete, intelligible
explanatory material. Reject legal formulae, article labels, jargon and broken
units. Korean is unsupported. DGT overlaps JRC-Acquis; do not count both as
independent replenishment. Reconstruct document provenance before pack admission.

## EUbookshop: explanatory European material, conditional rights

[OPUS v2 metadata](https://github.com/Helsinki-NLP/OPUS/blob/42d4fbe382245487a68e853ca53bea832a41a02a/corpus/EUbookshop/v2/README)
identifies publications collected by Tilde through LetsMT!. Brochures may offer
a better explanatory register than legislation; that is a suitability hypothesis.
Preserve publication IDs/edition/language and paragraph/segment ancestry; check
PDF extraction, repeated headers and 1:n alignments before counting targets.

The [Publications Office copyright notice](https://op.europa.eu/en/web/about-us/legal-notices/publications-office-of-the-european-union-copyright)
distinguishes website editorial CC BY 4.0 content from publication-specific
notices and third-party elements. It does **not** license the entire old corpus
under CC BY 4.0. Admit only publications whose actual notices cover redistributed
extracts; otherwise hold. The inspected pl–zh API record reports zero alignments:
no useful Chinese supply is demonstrated by that record. Korean supply is not
established. Retain as the second European-pair review candidate.

## Korean Parallel Corpora: useful scale, incomplete rights ancestry

The [publisher repository](https://github.com/jungyeul/korean-parallel-corpora/tree/a1fb53dc5216f3a2d07d973456d8921ed23c88c6)
declares **CC BY-SA 3.0 Unported** for the corpora.
[Korpora's maintained loader documentation](https://ko-nlp.github.io/Korpora/en-docs/corpuslist/korean_parallel_koen_news.html)
reports 97,123 pairs across splits; reserve dev/test for evaluation rather than
promising all 97,123 as production supply. Only `korean-english-news-v1` is in
this review: exclude Bible, North-Korean, JHE and contributed folders.

[Park–Hong–Cha 2016, section 5.2](https://aclanthology.org/Y16-2002.pdf)
describes news crawled from Yahoo! Korea and Joins CNN during 2010–2011 and warns
of alignment errors. Its broad MIT statement does not replace the current
repository's dataset terms. A declared corpus grant is not independent evidence
of permission from upstream news rightsholders. Verify original/translation
ancestry and redistribution coverage before admission; keep this gate open.
Preserve author/title/terms, source IDs and adaptation notices under BY-SA.
This candidate supplies ko–en only; English matching cannot manufacture pl–ko.

## Europarl: sizeable speech, extraction permission still open

[Publisher](https://www.statmt.org/europarl/) describes multilingual parliamentary
proceedings, automatic alignment and document/speaker markup; its absence-of-known-
restrictions statement is not a specific content licence. The
[Parliament legal notice](https://www.europarl.europa.eu/legal-notice/en/home)
permits attributed commercial/non-commercial dissemination under conditions,
including reproduction of the entire item. Whether the proposed detached sentence
and chunk adaptation satisfies those conditions is unresolved. No blanket CC0,
CC BY or CC BY-SA grant was verified for this exact corpus.

Hold production use until an applicable extract/adaptation grant is established.
If cleared, measure speeches in the proper collection; parliamentary procedure,
political assertions and speaker-dependent fragments require domain checks.
Credit Parliament, Koehn/Europarl and OPUS; retain proceedings/date/speaker/segment
identities. Do not confuse this text corpus with separately licensed speech data.
There is no Korean coverage or demonstrated Everyday replenishment here.

## MultiParaCrawl: strongest direct gap coverage, rights hold

The [pinned OPUS README](https://github.com/Helsinki-NLP/OPUS/blob/42d4fbe382245487a68e853ca53bea832a41a02a/corpus/MultiParaCrawl/v9b/README)
explicitly describes non-English bitexts made by **pivoting existing alignments
through English**. This is not verified direct human translation provenance.
Do not confuse alignment joins with generated MT, or admit either automatically.
Score thresholds alone cannot certify correspondence.

The [ParaCrawl publisher licence](https://paracrawl.eu/)
grants CC0 to **packaging**, explicitly not ownership of all extracted texts.
The OPUS CC0 label does not cure that limitation. The only possible admission
route is an auditable subset with original/translation URLs, applicable content
grants and preserved alignment anchors; availability and useful yield of such a
subset are unmeasured. If the distributed format lacks this evidence, fail closed.
Exclude separately offered manufactured/synthesized material.

| Korean pair in v9b | API alignment records |
| --- | ---: |
| de–ko | 1,284,016 |
| fr–ko | 951,139 |
| ko–zh | 831,643 |
| ko–pt | 606,677 |
| it–ko | 597,632 |
| ko–pl | 250,888 |
| ko–ru | 78,481 |
| es–ko | Unknown; metadata field empty |

The inspected response has no ja–ko entry: do not infer Japanese coverage.
English pairs belong to ParaCrawl, not MultiParaCrawl. OPUS ParaCrawl v9 en–ko
reports 4,002,522 records; ParaCrawl-Bonus v9 reports 7,709,467. They are distinct
metadata entries, not independent gains to sum. These counts do not remove
the same underlying text-rights hold.

## Large alternatives that are not ready replacements

- **CCMatrix / NLLB:** both inspected pl–ko v1 entries report 7,617,412, and
  both pl–pt entries 33,929,762. Do not sum identical counts or assume independent
  material. [CCMatrix publisher](https://github.com/facebookresearch/LASER/tree/main/tasks/CCMatrix)
  describes automatic web mining; its software licence does not establish
  rights to every mined text. [AllenAI's NLLB card](https://huggingface.co/datasets/allenai/nllb)
  and the pinned OPUS NLLB README declare **ODC-By**; the
  [licence](https://opendatacommons.org/licenses/by/1-0/) covers database rights,
  not necessarily separate rights in its contents. The card describes mixed web
  origins, possible MT and URL fields that can be absent. Underlying grants and
  human translation ancestry remain unresolved. NLLB/model/repository licences
  must not be interchanged. Not selected ahead of the smaller, better traceable
  candidates; no NC licence is inferred from a missing licence file.
- **OpenSubtitles v2024:** 22,910,821 pl–ko records look attractive, but
  [release metadata](https://github.com/Helsinki-NLP/OPUS/blob/42d4fbe382245487a68e853ca53bea832a41a02a/corpus/OpenSubtitles/v2024/README)
  does not establish all subtitle text rights. Repetition/alternative uploads,
  fragmentary dialogue and alignment add quality risks. Earlier hold stands.
- **HPLT:** [publisher terms](https://data.hplt-project.org/two/) likewise
  distinguish CC0 packaging from underlying text rights. Vast monolingual byte
  totals are not parallel supply and do not repair translation gaps by themselves.
- **QED/TED, NIKL, MASSIVE and WikiMatrix:** retain the existing
  [candidate exclusions](CORPUS-CANDIDATES-0.12.md) and
  [measured decisions](PARTS-6-9-DECISION-RECORD.md). A new scale headline is not
  new licence or quality evidence. Wiki titles, UI strings and named-entity lists
  are not substitutes for complete natural contexts.

## Next bounded round, before any final assembly

1. Review DGT pl–pt/it–pl and rights-cleared EUbookshop publications first for
   European explanatory supply. For Korean, first settle the news-v1 upstream
   rights and MultiParaCrawl URL/grant/anchor feasibility; do not download a giant
   corpus merely to discover missing provenance.
2. Pin exact selected files, sizes, hashes and text terms; raw API hashes here
   identify metadata only. Preserve original source/domain/licence ancestry.
3. Measure both directions against accepted baseline targets, keeping baseline
   ranks for comparison. Report unknown vocabulary separately; do not invent ranks
   or change frequency levels to imply incremental gain.
4. Report new unique targets, distinct useful contexts, overlap with every existing
   source, rejects by reason, allocation losses, and per-level 40/1,000 transitions.
   Count learner memories separately from memberships. Measure the candidate's
   intended collection; do not compare news output to an Everyday inclusion floor.
5. Run automatic shape/language/duplicate/anchor checks and bounded deterministic
   semantic/domain samples. No exhaustive owner review. Missing rights or bad
   translations stop admission even when counts are large. Keep reusable reports
   and rejects; full-matrix expansion follows a useful initial result.

Full Catalogue v2 assembly remains deferred until supply decisions, current-data
validation, final byte/reader constraints and licence-honest source handoff pass.
If no allowed useful corpus closes a gap, retain the honest shortage. The next
round must not weaken quality gates, generate filler or reopen rejected sources
without materially new evidence.
