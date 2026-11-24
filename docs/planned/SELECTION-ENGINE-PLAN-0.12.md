# Selection engine: performance and classifier evaluation plan

Checkpoint: 2026-10-09, Asia/Yekaterinburg. The owner requested documentation
of the proposed next round. **This is a plan, not an implemented engine or an
approved classifier.** The active [0.12 plan](PLAN-0.12.md) owns sequencing;
[Catalogue v2](CATALOGUE-V2.md) and [source policy](SOURCES.md) retain their gates.

## Purpose and limits

Before scaling candidate corpora further, measure the current processing costs,
improve reuse where justified and test whether an additional content classifier
offers enough benefit. Keep the existing accepted Everyday snapshot as a control.
Do not acquire giant new corpora or repeat already completed workflows merely to
evaluate this design. Full Catalogue assembly and publication remain deferred.

The owner wants fast, deterministic offline evaluation without an LLM service.
Determinism means identical pinned inputs, model, settings and execution contract
produce identical decisions. Conventional learned classifiers can satisfy that
requirement; being learned does not imply stochastic inference or an LLM wrapper.
It must be demonstrated for the chosen implementation, not assumed from its name.

No classifier is selected yet. Keeping the current rules is a valid outcome if
the measured additional benefit does not justify time, memory and maintenance.
A classifier cannot create missing source translations or cure licence gaps.

## Observed implementation, not a timing diagnosis

Read-only inspection found:

- `everyday_selection.validate_pool` reads and validates the full supplied pool.
  `selection_experiment.stage` subsequently parses candidates into a fresh SQLite
  database. The staging path is recreated for a new selection invocation.
- Frequency ranks and per-context choices are already reused between pairs within
  a run. Japanese boundary checks use a pinned offline analyzer/dictionary.
  Preserve those improvements rather than proposing them as missing functionality.
- Current checks cover structural constraints, exact identity, token boundaries,
  ambiguous occurrences, quarantine and context diversity. Context ordering uses
  source/alignment evidence and compactness; that is not semantic certification.

Multiple passes are **profiling candidates**, not proven bottlenecks. No stage
timing, speedup, classifier accuracy or full-run RAM result was measured during
this planning update. Required input verification must not be removed for speed.

## Planned work sequence

1. **Profile the existing path.** Use pinned saved inputs and a bounded,
   representative diagnostic scope before any large run. Record hardware/runtime,
   input bytes/records, elapsed time and peak memory by verification, staging,
   rank construction, segmentation/analysis, pair evidence, context allocation and
   output. Separate cold and reused-input conditions. Do not extrapolate a small
   sample into a guaranteed full-workflow duration.
2. **Choose reuse from measured costs.** Investigate reusable validated staging,
   analysis/features and completed shards. Identify caches by input content hashes,
   source registry, schema and relevant analyzer/rank/policy versions. A changed
   policy invalidates its dependent results, not silently the source evidence.
   Do not reuse selected choices under changed ranks or function-word settings.
   Preserve stable traversal, frequency tie order, exact deduplication and safe
   interruption/resume. Complete dependency/integrity checks precede reuse.
3. **Separate mandatory checks from content scores.** Licence/provenance, original
   source text, schema, offsets and exact identity remain hard gates. Evaluate
   translation correspondence, collection suitability and target-in-context
   usefulness as distinct questions. A fluent pair can still be mistranslated;
   a correct legal translation can still be unsuitable for Everyday.
4. **Compare a small number of approaches.** Start with current rules versus one
   feasible established offline classifier. Consider a lightweight custom model
   only if a specific unmet need is demonstrated. Classify unique text pairs or
   contexts once and reuse their scores where applicable; reserve more expensive
   analysis for unresolved candidates. Document unsupported languages explicitly.
5. **Decide before integration.** Retain a classifier only if it catches additional
   independently established defects with acceptable loss of good material and
   acceptable resource cost. Model-based policy changes receive a separate policy
   version and before/after decision report. No automatic production admission.

Performance-only changes must preserve selected target IDs, memberships, contexts,
origins, frequency levels and decisions on the same inputs. Compare canonical
content/hashes separately from intentionally variable timing diagnostics. If
semantics change, label that experiment separately; do not call it a pure speedup.

## Candidate approaches to investigate

| Approach | Possible role | Evidence still needed |
| --- | --- | --- |
| Existing rules | Mandatory checks and baseline | Runtime profile; independently identified missed defects and false rejections |
| Bicleaner | Score whether paired sentences are translations | Exact language-pack coverage, model/data terms, CPU/memory cost and ikna-specific accuracy |
| OpusFilter | Configurable features and combined quality/domain scoring | Select only useful components; verify dependencies, reproducibility and incremental benefit |
| Lightweight custom classifier | A narrowly defined gap, such as collection suitability | Suitable training labels, independent holdout, per-language performance and maintenance justification |

Research references checked 2026-10-09:
[Bicleaner publisher](https://github.com/bitextor/bicleaner),
[OpusFilter authors' paper](https://aclanthology.org/2020.acl-demos.20/), and
[fastText classification documentation](https://fasttext.cc/docs/en/supervised-tutorial.html).
These demonstrate available approaches, not a chosen version, compatible model
licence, all-language coverage or measured speed on ikna. Source-code, weights,
dictionaries and training-data permissions must be assessed separately if used.

## Evaluation and reproducibility requirements

- Build a bounded, versioned control set stratified across languages, collections,
  source families and important defect types. Include the known failures and
  independently supported good cases. Keep training/calibration and held-out
  evaluation separate, including near-duplicate/source-document overlap.
- Do not train only to repeat current rule verdicts and claim independent quality
  improvement. Artificially mismatched pairs may be labelled test negatives, never
  catalogue material; success on easy negatives alone does not establish quality
  on natural mistakes. Missing trustworthy labels remain an explicit limitation.
- No exhaustive owner/native-language review of the catalogue is required. Use
  existing trusted references, reusable automatic checks and bounded review where
  needed. Agreement between heuristics/models is not translation ground truth.
- Report missed defects, false rejection of good examples and uncertainty by
  language/collection, with coverage and sample counts. Include retained unique
  targets, contexts, overlap, allocation losses and affected 40/1,000 thresholds.
  More accepted rows are not a quality metric by themselves.
- Report elapsed time, peak memory and artifact/cache size for the same inputs.
  Derive thresholds from the control evaluation; do not choose an arbitrary score
  and treat it as a calibrated probability. Uncertain cases remain recoverable
  with explicit reasons, not silently admitted or permanently discarded.
- Pin input/feature/model hashes, tokenizer/dictionary/dependency versions,
  inference settings, ordering and threshold policy. Test repeat runs and changes
  in batching/workers; training randomness is separate from inference. If numeric
  differences can flip a verdict, constrain the execution environment or defer
  those cases. A fixed seed alone is not proof of cross-platform reproducibility.

## Relationship to supply and final assembly

The [large-corpus shortlist](LARGE-CORPUS-CANDIDATES-0.12.md) and current-data
validation remain active. Prioritize performance diagnosis and the bounded
classifier decision before expanding expensive corpus experiments. Rights and
provenance feasibility research can continue without a full processing run.
WMT24++ and MKQA remain conditional supplements across eligible decks; no new
source is admitted by this plan. Preserve the 12,000 maximum, current source
decisions and separation of Everyday, Knowledge and World.

Expected next-round deliverables: stage profile, justified reuse design/change,
content-evaluation contract, bounded comparison report and an explicit decision
to retain existing rules, trial a ready classifier or justify a custom one.
Final packs, publication, runtime app classifiers and learner-state changes are
outside this round. The implementation work has not begun in this checkpoint.

## Implementation checkpoint — 2026-10-11

[Round 23](ROUND-0.12-23.md) prepares a bounded diagnostic CLI and output-preservation
regressions. The fixture-only profile is not a bottleneck measurement. Step 1 is
still open pending the saved admitted pool, representative bounded derivation,
isolated allocation/output timing, stage memory and controlled cold/reused-input
evidence. No cache, model, new source or production-policy change is selected.

## Real-input checkpoint — 2026-10-12, Asia/Yekaterinburg

[Round 24](ROUND-0.12-24.md) restores/verifies the full saved pool and records a
repeatable 14,012-row, 110-direction diagnostic with unchanged selection. The
largest observed bounded boundary is context analysis; this motivates reuse
investigation but proves no full-workflow speedup. Sample ranks and ICU 76.1 differ
from the historical full snapshot contract. Stage memory, isolated allocation,
controlled cold/reuse evidence and independent classifier labels remain open.
No cache or classifier is integrated, and production snapshot decisions stay fixed.

## Stage/replay checkpoint — 2026-10-12, Asia/Yekaterinburg

[Round 25](ROUND-0.12-25.md) supplies disjoint selection windows, isolated allocation,
sampled RSS with explicit uncertainty, two fresh-process/repeated-process comparisons
on 14,012 and 105,988 saved-source rows, and exact output equivalence against untouched
round-22 code under pinned ICU 74.2. Original historical binary hashes and OS-cold/
full-corpus timings remain unavailable/unmeasured; no contrary claim is made.

The measured next experiment is context-analysis reuse only. Cache returned quality
counts as well as rows, bind exact text/ranks/runtime/policy dependencies, keep all
provenance validation and downstream selection active, and require end-to-end
identity/cost evidence before integration. Classifier comparison still needs its
independent quality labels and rights/reproducibility decision.

## Reuse checkpoint — 2026-10-12, Asia/Yekaterinburg

[Round 27](ROUND-0.12-27.md) implements and measures exact context-analysis reuse
behind a standalone experiment. Returned quality counters and rows are preserved;
changed dependencies invalidate keys and incomplete/corrupt entries fail closed.
Warm-cache savings on the 105,988-row diagnostic are observed, with a first-use
penalty. Keep production selection unchanged pending full-input resource/cost
measurement. Full-corpus speed, OS-cold behavior and semantic classifier accuracy
are not claimed by this checkpoint.

## Content-evaluation checkpoint — 2026-10-12, Asia/Yekaterinburg

[Round 28](ROUND-0.12-28.md) separates mandatory gates from correspondence,
collection suitability and cloze usefulness; audits one established classifier's
coverage and prepares an independent ID-only label manifest. Retain rules now.
Steps 4–5's empirical score/benefit comparison remains unperformed: individual
pack rights/contents, pinned startup and benchmark-use questions are unresolved.
A feasible toolkit is not evidence of an accurate 110-direction classifier.

## Full-input readiness — 2026-10-12, Asia/Yekaterinburg

[Round 29](ROUND-0.12-29.md) prepares safe resource-bounded full saved-pool profiling,
with successful-validation/partial-stage evidence retained on later failure.
No full-run result exists yet in this checkpoint. Use the accepted historical
8000cap control explicitly; compare logical output separately from compressed
bytes because historical/local Python and zlib versions differ. No cache enabled.

## Round 30 — full saved-input control verified

[Round 30](ROUND-0.12-30.md) completes all 11,099,490 candidates and 110 directions
under historical cap 8000. Independent whole-output checks reproduce both logical
and compressed historical selection hashes, all counts and deck decisions. The
full stage profile identifies pair evidence as the dominant cost; bounded
analysis-cache savings must not be extrapolated to the complete workflow.
No cache is enabled. A bounded exact pair-function profile is the next diagnostic.
The current cap-12000 scenario, OS-cold measurement, semantic classifier scoring,
final assembly and publication remain outside this completed control.

The one-pack [read-only archive audit](CLASSIFIER-ARCHIVE-AUDIT-0.12.md) is complete;
pack contents do not resolve underlying source provenance or empirical quality.
Model execution and classifier adoption remain deferred.

## Round 31 — exact dense-prefix cost localized

[Round 31](ROUND-0.12-31.md) measures the unchanged pair-evidence function on an
exact 32,768-candidate en→de prefix from finalized full staging. Fresh baseline
and cProfile return byte-identical complete evidence. Repeated overlap maxima
and Counter intersections dominate evidence pruning in this diagnostic.
Profiler overhead is not an engine gain; one prefix does not represent all pairs.
The next scoped prototype may reuse exact call-local overlap maxima while
preserving all ranking, source, boundary, duplicate and output decisions.
Production integration still requires differential and real-output evidence.

## Round 32 — bounded exact overlap reuse verified

[Round 32](ROUND-0.12-32.md) verifies the call-local maximum-reuse prototype with
3,177 differential invocations, real multilingual/Japanese regressions and seven
exact-output real-prefix runs. Three fresh original/candidate runs observe median
13.494 s versus 4.658 s on that prefix. LRU recency changes are disclosed and
included in measurement. Normal production source remains unchanged.
An isolated full historical candidate replay is the next acceptance gate and
started separately as Round 33; prefix gains do not establish full-workflow gains.

## Round 33 — full exact candidate accepted

[Round 33](ROUND-0.12-33.md) completes the isolated overlap-reuse replay over all
11,099,490 candidates and 110 directions under historical cap 8000. Independent
whole-output CRC/hash verification proves byte-identical selected material,
counts, decks and quality counters; only the reviewed policy fingerprint differs.
Observed elapsed time is 4288.253 s versus 8335.166 s control (48.55% lower), with
shared-host/cache and one-run uncertainty explicitly retained. No guard stopped.
This closes the full-output/resource gate for the exact call-local optimization.
Normal source is unchanged in this checkpoint; integration and durable
independent-reference regression tests are the next separate stage.

## Round 34 — exact overlap optimization integrated

[Round 34](ROUND-0.12-34.md) integrates only the fully replayed call-local maximum
reuse function and three test-only files. 143 relevant tests pass; explicit
Japanese-engine rerun and a 105,988-row normal-CLI smoke preserve all expected
material identities. No UI, policy semantics, default cap or persistent cache
changes. Original and isolated full candidate evidence plus rollback checkpoint
are retained. The observed full-control elapsed reduction is 48.55%, with
one-run/shared-host/cache limitations.

Performance profiling and this justified exact reuse change are complete within
the reported scope. Empirical classifier scoring/adoption stays deferred on
provenance/rights, labels/runtime and coverage grounds; retain current rules.
Android SDK agreement/setup and real-platform GUI acceptance remain separate
application gates. No final Catalogue assembly, source expansion or publication.
