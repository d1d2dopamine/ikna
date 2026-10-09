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
