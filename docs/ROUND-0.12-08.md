# 0.12 round 08 — admitted Everyday selection, 2026-10-06

## Scope and evidence

Owner authorized continuation to Catalogue v2 after reporting the round-07 window
artifact fixed. Acceptance is owner-reported; no exhaustive platform matrix was
supplied. Window/application source is preserved. Both repository skills apply.

Baseline: complete 575-file round-07 ZIP, 6,445,998 bytes, SHA-256
`4ee9fecca3cbbb9e9e63921e417c7f8e8344608b6184f9eb0a7535140bb3c885`.
Every source byte matched before editing. The active plan retains Tatoeba-only
Everyday, rejects MASSIVE admission/all-direct WikiMatrix expansion, and requires
verified all-document provenance before World can enter the freeze.

Read-only GitHub REST observations and response hashes are retained in
[everyday-round08.json](evidence/everyday-round08.json). The exact final admitted
pool artifact query returns zero records; old Everyday run `34896470535` and World
run `35009216395` also list zero artifacts. The admitted-pool workflow is not
listed at this public checkpoint and its run query returns 404. This is metadata,
not a source comparison with the owner's unpushed work. No private session or
credentials were probed. The first generic artifacts page is not a complete
repository inventory; the exact named/run queries support the observations above.

No complete production candidate input is available in this session. Old Everyday
artifacts had retained reports and bounded previews, not full candidate inputs.
Their success and the completed supply census remain evidence; they cannot
reconstruct the missing final Tatoeba-only pool. Separately saved local inputs
may still exist and should be reused. World remains open; no new World run is
requested to compensate for missing detailed reports.

## Confirmed bugs and changes

Executed the baseline selection code on disposable fixtures and reproduced:

- Staging at the candidate path replaced input bytes with a SQLite file before
  the run failed. Validate protected/distinct paths before mutation, including
  resolved symlinks/existing hard links and morphology inputs. The public staging
  helper independently rejects a candidate alias too.
- Invalid UTF-8 was silently replaced with U+FFFD. Plain/gzip selection now fail
  on invalid UTF-8 rather than making altered content part of the evidence.
- Identical preview rows written to different gzip names produced different bytes.
  Use an empty gzip filename and `mtime=0`; JSON/UTF-8 writing stays deterministic.

New `everyday_selection.py` binds the existing deterministic selection engine to
a full admitted Part 7 pool/report. Validate raw/logical SHA-256, sizes/counts,
registry identity, source status/version, exact recorded scope and every origin.
Reject mixed-source/experimental previews, foreign scope, contradictory duplicate
counts and changes during validation/selection. Use temporary staging so failure
does not replace the prior successful staging result.

Selection inherits the pool's scope/function-word sieve, includes every planned
directed pair/level (zero-source omissions too), and records input/pipeline and
environment identities. Existing defaults are unchanged: 8,000 safety budget,
40 minimum, 1,000 thin threshold, three contexts per target. No filling, target
identity change, runtime sentence synthesis or learner-data mutation.

The review JSONL/Markdown keeps at most ten policy-ranked targets per publishable
deck with their context/meaning references and origin metadata. These are bounded,
rank-biased review aids, not full selected assets, semantic-quality statistics or
native-speaker proof. Automated source admission does not complete material review.
`publicationSafe=false`, `materialReviewCompleted=false`, `contentFrozen=false`.

The existing **catalogue v2 everyday admitted pool** workflow now optionally runs
this Part 10 review (`run_selection=true` by default). It uploads the full pool
**before** selection, then retains review evidence in a separate same-run artifact.
A selection failure therefore need not cause another corpus acquisition. Both
artifacts use 90-day retention; save the full input before expiry. CI and workflow
pre-acquisition contracts include the new regressions.

## Checks actually executed

- `test_everyday_selection.py`: **15 unittest cases passed**, covering identity,
  all-origin/source/scope gates, no-source inventory, UTF-8, byte reproducibility,
  symlink/hard-link/output protection, previous-result preservation, mutation
  detection, limits, Chinese/Japanese/Korean identity/context preservation and an
  actual CLI run. Fixture `minDeck=1` is test-only.
- `test_everyday_pool.py`: **11 tests passed**. Existing selection-policy,
  Everyday-rebuild and ingestion contract scripts passed; segmentation's
  **16 tests passed**, including ICU/UTF-16 and no character fallback.
- Both changed workflow YAML files parse; **18 Bash blocks** pass `bash -n`,
  **3 Python heredocs** parse. Input upload precedes selection; permissions remain
  `contents: read`, `actions: read`.
- **8 invalid workflow preflight choices** were rejected. Four actual workflow
  shell blocks executed offline: input validation, exact artifact-file lookup,
  full admitted pool build and Part 10 review. Their report hashes agree; missing
  `en->fr` is explicit. Production defaults omit the tiny fixture without padding.
  This does not test remote Actions transfer or a real Tatoeba export.
- Text, localization, owning document links, final byte diff and exact archive
  verification are recorded after execution in the JSON evidence.

No corpus download/full census, remote workflow dispatch, public publication,
Kotlin/Gradle build or new Windows test was performed. Real full input, selected
material review/final policy, content census/freeze and storage/client gates stay
open. Local fixtures cannot close those gates.

## One next owner run

Commit this complete ZIP to the default branch. Then run **catalogue v2 everyday
admitted pool** with `input_mode=fresh-tatoeba` if no separately saved complete
candidate input exists; keep both eleven-language defaults and
`run_selection=true`. Artifact-specific input pins are unused for fresh mode.
This acquires the missing final admitted input, not a repeat of known admission
experiments or supply census. Prefer exact artifact reuse when a saved full input
is available. [EVERYDAY-POOL.md](EVERYDAY-POOL.md) owns both routes and commands.

Send the run link and preferably both artifacts. For initial review the needed
files are `EVERYDAY-POOL.json`, `EVERYDAY-SELECTION.json`,
`EVERYDAY-SELECTED-SAMPLES.md`, `INPUT-REFERENCE.json`; keep the full pool locally.
If selection fails, reuse the prior uploaded pool with that run's version/hash.
No additional World/Knowledge run is requested here.

## Delivery

One complete 579-file ZIP: all 575 baseline files plus
`tools/catalog/everyday_selection.py`, `tools/catalog/test_everyday_selection.py`,
this report and `docs/evidence/everyday-round08.json`. Existing changes are scoped
to selection, the two owning workflows and documentation/status records. No source
deletions or signing/schema/build/dependency/skill/application-source changes.
