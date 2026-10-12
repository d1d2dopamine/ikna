# Offline classifier feasibility, round 28

Research checkpoint: 2026-10-12 Asia/Yekaterinburg (2026-10-11 UTC).
Read-only publisher/source inspection plus a small evaluation-manifest preparation.
**Decision: retain current rules; defer classifier integration and the scored
comparison.** This closes a feasibility investigation, not the empirical accuracy
experiment in selection-plan steps 4–5. No classifier was installed, executed,
selected for production, or used to admit/discard a catalogue row. No weights or
large corpora were downloaded. The independent label source below is a conditional
external benchmark, not a newly admitted source.

## What each decision means

The current `boundary-diversity-v2` checks and source registry remain authoritative
for structural validity, direct-link provenance, licence, exact original text,
UTF-16 occurrence/boundary integrity, quarantine and diversity. They are **not**
labels of semantic correctness. Keep three independent content fields:

1. Translation correspondence: whether the two original sentences convey the
   same meaning, including omissions, additions, negation, entities and numbers.
2. Everyday suitability: ordinary usable language, register and standalone
   relevance. A correct news/legal translation need not qualify.
3. Target-in-context usefulness: the specific selected target has an unambiguous,
   pedagogically useful contextual sense. Whole-sentence equivalence does not
   establish the meaning of one cloze target.

A future score is supplemental evidence with uncertainty, never permission to
skip mandatory gates. External MT examples cannot pass through the Tatoeba source
registry; evaluate content-only rules separately from source admission. Otherwise
all benchmark rows fail provenance and the comparison is meaningless.

The current 12,000 cap, 40/1,000 supply thresholds, accepted Everyday control,
WMT24++/MKQA conditional status and separation of Everyday/Knowledge/World are
unchanged. A model cannot repair missing direct translations or permissions.

## One concrete candidate: classic Bicleaner 0.17.4

Use classic Bicleaner, **not Bicleaner AI**, if the deferred feasibility gates are
later resolved. It is an established offline feature-based classifier; publisher
code defaults to Extra Trees and supplies `bicleaner-classify-lite`. The latter is
a single-process implementation, not a smaller neural model. Nothing here calls
an LLM or remote inference API.

Verified publisher pins:

- [Code tag bicleaner-v0.17.4](https://github.com/bitextor/bicleaner/tree/bicleaner-v0.17.4),
  commit `2b728c1574928c3d37e44ba2e73b3b7959b68b31`.
- [PyPI 0.17.4 metadata](https://pypi.org/pypi/bicleaner/0.17.4/json): wheel
  `bicleaner-0.17.4-py3-none-any.whl`, 52,104 bytes, SHA-256
  `acec8bc350a2fbc20c8000e80b270de681f636aa1cea8177dc328abfc6d472fa`.
- [Latest classic model release v1.6](https://github.com/bitextor/bicleaner-data/releases/tag/v1.6),
  published 2022-12-15. Release notes explicitly say trained with Bicleaner 0.16
  and tokenized with Sacremoses `-x`. This is distinct from the code version.
- [Pinned dependency specification](https://github.com/bitextor/bicleaner/blob/2b728c1574928c3d37e44ba2e73b3b7959b68b31/pyproject.toml)
  fixes scikit-learn 1.1.3, Sacremoses 0.0.53, pycld2 0.41 and
  bicleaner-hardrules 2.10.3; several other dependencies are ranges/unpinned.
  Therefore upstream installation instructions are not a complete reproducible
  environment. Freeze all transitive wheels, Python/OS/BLAS and hashes first.

### Exact downloadable coverage

The current release has 33 language-pair packs and one auxiliary FastSpell pack.
Its complete list is in `evidence/classifier-round28/publisher-assets-and-coverage.json`.
All five published classic releases were inspected; older releases do not fill
ikna's missing Japanese, Korean or Chinese pairs.

| Classic v1.6 asset relevant to ikna | Compressed bytes |
| --- | ---: |
| en-de.tar.gz | 523,179,851 |
| en-es.tar.gz | 351,612,333 |
| en-fr.tar.gz | 301,237,052 |
| en-it.tar.gz | 282,449,727 |
| en-pl.tar.gz | 380,483,224 |
| en-pt.tar.gz | 431,671,623 |
| en-ru.tar.gz | 508,534,409 |
| es-de.tar.gz | 663,843,764 |

These cover **8/55 unordered pairs, at most 16/110 learner directions**, by putting
columns in the pack's trained order and reusing one pair score for reverse learner
presentation. This is not proof of reverse-direction accuracy or a license to
swap a pack's source/target roles. The JSON records all 110 directions explicitly.

`pl` has only an English pack; its nine other unordered pairings are unsupported.
`ko`, `ja` and `zh` have no classic pack in these releases. There is no pivoting,
translation generation or substitution of neural Bicleaner AI models. Unsupported
means rules-only and no model-score claim, not a score of zero. Downloading all
eight relevant packs would cost 3,443,011,983 compressed bytes before dependencies
or extraction, and would still leave 94 directions without a direct pack.

### Separate rights and provenance findings

| Component | Publisher evidence | Remaining constraint |
| --- | --- | --- |
| Bicleaner source | GPL-3.0 in code repository and package metadata | Keep notices/source obligations if distributing software; offline use does not make this a new sentence-data licence |
| Classifier weights | `bicleaner-data` repository declares GPL-3.0 and describes itself as trained models/resources | Archives were not downloaded; per-file notices and model YAML not inspected. Do not call an individual weight file fully audited yet |
| Probabilistic dictionaries and word-frequency lists | Included resource category in same GPL-3.0 data repository; classifier needs both directions and frequency lists | No separate per-pack source-corpus/licence inventory was found in the README or release metadata |
| Training sentences and derived resources | Release says code version/tokenization used | Exact datasets, split dates, filtering and train/test overlap are not documented in reviewed metadata; do not infer OPUS-wide or ParaCrawl-wide rights |
| Optional fluency/content/language helpers | Hardrules dependency and code refer to KenLM, FastText/FastSpell/Hunspell-related resources | Those code/weights/dictionary terms are separate. Disable optional LM/content filtering for the first score-only trial; still audit installed dependencies |

Primary [data repository](https://github.com/bitextor/bicleaner-data) and
[licence](https://github.com/bitextor/bicleaner-data/blob/c9e9f0d08e40f01d47b5afb165ed032b25bad7c1/LICENSE).
GPL declaration is positive evidence, not evidence that all underlying training
texts can be redistributed as cards. No claim of illegality is made; these are
unresolved provenance/pack-inspection gates under ikna's conservative policy.

### Resource and reproducibility feasibility

A single en-de pack is a bounded possible next download, not a tiny baseline.
Publisher metadata supplies byte sizes and stable asset IDs/URLs but every v1.6
asset's GitHub `digest` field is null, and no checksum sidecar is listed. Compute
and pin the downloaded archive and every extracted file before execution; a tag
alone is not a content checksum. Trusted-publisher joblib models are executable
pickle-family artifacts: load only verified official assets in an isolated,
network-disabled environment, never arbitrary candidate-provided paths.

The current host reports approximately 9.7 GiB RAM, nine online CPUs and 25 GiB
free disk. These are host observations, not guaranteed task limits. Extracted
pack size, classifier startup/RSS and seconds per pair are **unmeasured**. Source
inspection shows eager loading of the classifier and both probabilistic
dictionaries/frequency data, so reducing input rows does not eliminate startup
memory cost. Do not extrapolate compressed bytes to RAM. A possible trial should
use one process and hard resource ceilings (proposed, not measured): 4 GiB RSS,
4 GiB additional disk and 10 minutes for startup plus 500 pairs. Abort and report
if a ceiling is exceeded; never silently weaken integrity checks.

For a pure correspondence trial, keep ikna gates outside the scorer and test
`--disable_hardrules --disable_lm_filter --disable_porn_removal` with lite scoring.
Inspect startup first: lite still instantiates Hardrules, so disabled score paths
do not prove all optional assets are avoided. Do not blindly run upstream tests,
which download a language pack. Use the exact pack tokenizer contract and fixed
BLAS thread settings. Record scores and verdicts over two fresh processes, a
reordered input restored by IDs, and different batches/workers. Near-threshold
numeric disagreements stay unresolved. No calibrated probability or default
0.5 rejection threshold is claimed.

## Independent human labels: a small conditional external benchmark

[Google's WMT MQM repository](https://github.com/google/wmt-mqm-human-evaluation)
provides professional-translator annotations, including error category/severity,
source and system output. Its
[Apache-2.0 licence](https://github.com/google/wmt-mqm-human-evaluation/blob/29acd6999aaea3586378c9dcb58d8cc4820cede3/LICENSE)
expressly permits use/reproduction/distribution subject to its conditions. Retain
licence/notices, cite Freitag et al., and identify modified extracts. However,
[TED's own terms](https://www.ted.com/about/our-organization/our-policies-terms/ted-com-terms-of-use)
state CC BY-NC-ND terms for transcripts where applicable. The repository licence
is not proof that original TED text has been relicensed without restrictions.
**Research candidate only: do not redistribute benchmark sentence text or admit it
to packs; reconcile original-text terms before a broader commercial evaluation or
redistributed text fixture.** The saved manifest contains IDs/hashes and labels,
not sentences. This explicit rights caveat is one reason the trial remains deferred.

Verified small file:

- Commit `29acd6999aaea3586378c9dcb58d8cc4820cede3`;
  [ted/ende/mqm_ted_ende.tsv](https://raw.githubusercontent.com/google/wmt-mqm-human-evaluation/29acd6999aaea3586378c9dcb58d8cc4820cede3/ted/ende/mqm_ted_ende.tsv).
- 2,341,948 bytes; SHA-256
  `3952dc904997dda31c03421243862cb8a093f9a712fd90dc97b9709b4bb387b3`.
- Locally inspected: 8,435 annotation rows; 7,406 system/document/segment groups;
  529 source segments across five talks and 14 system labels in this exact file.
  README's broader submission count must not replace actual file inventory.
- 868 groups contain a Major Accuracy error; 4,404 have only explicit No-error
  annotations. Every group has **one** rater. The latter label is observed
  single-rater absence of marked errors, not certified correctness or consensus.
- Prepared `mqm-evaluation-manifest.json`: 100 calibration pairs from talk.1/3
  (50 errors, 50 no-error), 200 held-out from talk.4/5/6 (100 each). Hash-ordered,
  source-text-unique selection; all translations of one document stay in one
  split. `prepare_mqm_manifest.py` reproduces it from the pinned file. Actual
  class prevalence is deliberately not represented by this balanced sample.
- Preserve original annotated text separately; remove only `<v>`/`</v>` span
  markers for scorer input, verify group text consistency, and retain row/rater
  provenance. Minor/style/fluency-only cases are excluded from binary primary
  labels and remain a secondary diagnostic, not converted to errors by rules.

These labels are independent of ikna's rules and easy synthetic mismatches, but
not an independently proven Tatoeba holdout. They test MT translation errors in
TED speech, not Everyday suitability or cloze usefulness. No `pl`/`ko` evaluation
is provided here. Exact hashes prevent exact leakage; paraphrase/source overlap
and unknown model training overlap remain to audit. Temporal independence is not
proven: TED labels predate classic model release v1.6. A successful benchmark
cannot authorize full-matrix/Tatoeba deployment.

## Why not substitute OpusFilter now?

[OpusFilter](https://github.com/Helsinki-NLP/OpusFilter) is MIT-licensed toolkit code;
[PyPI](https://pypi.org/pypi/opusfilter/json) currently reports 3.3.1, with wheel
SHA-256 `832e0369301f73ec66371f25c6362f0e6a4a490acc3b96617d63d3f63ea4429b`.
Its [classifier documentation](https://github.com/Helsinki-NLP/OpusFilter/blob/8d7afda9c1cc89bcc8a4192afe88003b14797509/docs/functions/training_and_using_classifiers.md)
describes fitting an sklearn classifier (default LogisticRegression) from filter
scores; quantile-based positive/negative selection is not independent human
truth. No ready, licensed 110-direction semantic model was verified in the reviewed
publisher materials. Generic Unicode length/ratio/script features can run across
the matrix, but they do not establish translation equivalence. Adding word
alignment, language models or embeddings creates separate training, weights,
dictionary, tokenizer, cost and licence work. A fitted filter-score model would
be a new custom baseline, not a ready model silently covering Korean. Defer this
alternative rather than claiming that installing the MIT package closes the gap.

## Bounded comparison contract if gates are resolved

1. Audit one official en-de archive's contents/rights and content hashes; verify
   dependency install/resource ceiling offline. Keep rules unchanged.
2. Freeze the external benchmark manifest before scoring. Calibrate thresholds
   on its 100 calibration rows only; evaluate once on 200 held-out rows. Report
   error recall and no-error rejection with counts and confidence intervals,
   separated by error type and talk; cluster uncertainty by source/document.
   Balanced-sample precision is not expected Tatoeba production precision.
3. Compare content-only existing rules with rules plus classic score, then a
   separate diagnostic slice of at most 200 unique admitted Tatoeba en-de pairs.
   Tatoeba model disagreements remain unresolved unless independently supported;
   do not label them from either classifier or current rule verdicts. Include
   short sentences/known boundary cases and affected threshold-sensitive decks.
4. Report cold startup, scorer time/RSS, dependency/model/cache bytes, repeated
   score/verdict agreement and input/feature/model/runtime hashes. Report target,
   context and 40/1,000 threshold losses if any shadow policy is simulated.
5. Retain current rules unless the independent held-out comparison shows useful
   added defect detection at an explicitly accepted good-material-loss bound,
   and Tatoeba transfer evidence and costs justify it. No arbitrary accuracy,
   threshold or full-matrix success claim. `pl`/`ko` coverage is an explicit
   stopping issue for any claimed global classifier; do not sacrifice weak supply
   merely to increase model agreement. Production-policy changes require a
   separate version/review and before/after report.

### Status and next decision

The concrete next candidate is **one en-de classic Bicleaner pack**, conditional
on archive audit and external-label use terms. The present evidence supports
**deferral**, because resource use/model provenance remain unmeasured, the proposed
human labels have original-text rights/transfer/overlap limitations, and 94/110
directions are unsupported. The 300-pair manifest is prepared, not scored.
No empirical incremental benefit or performance result exists yet. No custom
classifier, production admission, runtime app dependency or publication follows
from this research.

## Round 30 follow-up — single-archive inspection

2026-10-12 UTC / Asia/Yekaterinburg. The round-28 statements above, including
"no weights downloaded" and the then-uninspected archive status, are preserved as
historical evidence. The bounded audit itself ran on 2026-10-11 at approximately
23:03–23:04 UTC, while the full saved-input profile was running. This follow-up was
integrated after that profile completed; no new download or execution was needed.

The [archive audit report](CLASSIFIER-ARCHIVE-AUDIT-0.12.md) and
[compact evidence](evidence/classifier-archive-round30/README.md) now establish:

- One official classic `v1.6` en-de archive, asset `88211159`, was received with
  exactly **523,179,851 bytes** and SHA-256
  `c932449563ef0108757382d7d2de2dae2bdd97f78cf9dcee3fcf734a80c5b98d`.
  This pins observed official bytes; the publisher digest is still null.
- A complete streaming inventory contains one directory and 14 regular files,
  **646,939,311** tar-stored payload bytes, with no links or unsafe member paths.
  Nested gzip resource sizes are not decoded dictionary sizes or runtime RAM.
- The only inspected payload is 1,182 bytes of inert model YAML. It confirms
  Extra Trees, `en` source / `de` target, feature version 4 and existing resource
  references. Its internal test histograms are not independent ikna accuracy.
- No standalone licence, notice, attribution or source-corpus inventory appears
  in the archive; the YAML adds no corpus/licence/split provenance. The separate
  repository GPL-3.0 text is preserved. Embedded notices in unread payloads remain
  unknown, and repository GPL does not establish every underlying-text right.
- Archive entries named `train.en-de` and `porn-removal.txt.en` were neither
  extracted nor read as sentences. No raw model/corpus/dictionary payload enters
  the source evidence package, catalogue or redistribution.
- Sequential low-priority download and metadata inspection used **18,128 KiB**
  and **12,812 KiB** maximum RSS, respectively, alongside the full profile.
  Elapsed times were 25.562 s and 2.149 s. These are audit-process measurements,
  not classifier loading/inference results or proof of zero concurrency impact.

**Decision remains defer.** Acquisition, archive hashing, full member listing and
bounded YAML inspection are complete. Resource/corpus rights, dependency freeze,
model startup/scoring costs, external-label permissions and overlap/transfer, and
measured incremental quality remain unresolved. No model was deserialized or run;
no threshold or admission policy changed. The 300-pair manifest remains unscored,
and this audit adds no coverage beyond the existing at-most-16/110 directions.
The current rules and conservative source gates remain authoritative.
