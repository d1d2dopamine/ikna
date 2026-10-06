# 0.12 round 02 — admitted Everyday pool and existing evidence

Reviewed 2026-10-06, Asia/Yekaterinburg. Common source baseline with the parallel
GLM lane: `ikna-0.12-press-fix-round-01.zip` (543 source files). This archive
contains this developer's lane only; GLM output has not been received or merged.
Both [project](../skills/ikna-development/SKILL.md) and
[owner](../skills/ikna-owner-workflow/SKILL.md) skills were applied.

## Existing runs inspected without rerunning corpora

The owner supplied the public repository and asked to reuse existing evidence.
Public Actions pages were inspected read-only. Metadata/status is distinguished
from detailed report evidence; logs require GitHub sign-in in this session.
No workflow was dispatched, retried or published.

| Evidence | Observed public result | What it establishes / remaining limit |
| --- | --- | --- |
| [Supply census #6](https://github.com/d1d2dopamine/ikna/actions/runs/35877105993), 2026-09-23 | Success, 1h 0m 2s | Matches the completed Part 5 run recorded in PLAN-0.11. Its retained measurements stay evidence; no new full census is requested |
| [Everyday experiment #2](https://github.com/d1d2dopamine/ikna/actions/runs/34896470535), commit `8f0870d`, 2026-09-15 | Success, 1h 38m 59s; contracts and experiment succeeded | Existing Part 6/10 experiment ran. Reviewed non-admission decision remains authoritative. Success alone neither admits MASSIVE nor creates a Tatoeba-only final pool |
| [Knowledge experiment #2](https://github.com/d1d2dopamine/ikna/actions/runs/34896496417), 2026-09-15 | Success, 25m 18s | Existing direct-pair experiment ran; the reviewed semantic-quality rejection remains, without score retuning or a new admission run |
| [World full provenance #1](https://github.com/d1d2dopamine/ikna/actions/runs/35009216395), commit `e73adaf`, 2026-09-15 | Prepare and all 32 shards succeeded; merge failed, exit 1; total 2h 6m 43s | A full pass was attempted. It cannot close Part 9. The exact cause and merged `completeScan`/retryable-document evidence were not available from the public status; inspect the existing report/log before deciding on repair/resume |
| [Build #199](https://github.com/d1d2dopamine/ikna/actions/runs/37366081452), commit `985ab34`, 2026-10-06 | Overall failure; Android migration job succeeded in 5m 49s; JVM job cancelled; platform jobs skipped | GitHub reports “The job was not acquired by Runner of type hosted even after multiple attempts” and an internal server error. This is a runner-allocation failure, not an observed Kotlin compilation failure |

The successful [migration job](https://github.com/d1d2dopamine/ikna/actions/runs/37366081452/job/111951470403)
lists application/instrumented APK build and real Android SQLite migration
validation. This is partial remote Android evidence for commit `985ab34`.
Detailed test counts/artifact contents were not inspected, and source bytes were
not independently compared with that commit. Shared JVM suites, desktop compilation,
packaged Windows hover/palette smoke and real Android palette accessibility remain
open. A migration pass does not establish visual correctness.

## Part 7 implementation

See [EVERYDAY-POOL.md](EVERYDAY-POOL.md) for the owning runbook.

- `everyday_pool.py` is separate from the historical MASSIVE comparison. Every
  provided record/origin is validated, including records outside the requested
  language scope; mixed/unadmitted sources, foreign collections, invalid references,
  moving versions and mismatched version/SHA-256 pins fail closed.
- Disk-backed exact deduplication retains every distinct origin and its contributor
  metadata. The saved complete scoped pool keeps alternative origins/meanings.
  Input/output aliases are rejected and bad input leaves the previous pool intact.
- The existing builder frequency/sieve rules run over only this Tatoeba scope.
  Every requested directed pair is reported, including zero supply. Global exact
  targets, pair/level memberships and primary-origin target/context memberships
  are separate; no cap, filling or additional learner memory is introduced.
- Identical pinned ordered inputs/environment produce the same gzip bytes across
  output names. Reports include raw/logical hashes and sizes, registry/pipeline
  hashes, Python/zlib/segmentation, source version and scope.
- The manual workflow defaults to exact artifact reuse with required run/name/file/
  version/hash. Missing/expired artifacts do not trigger a hidden new download.
  Explicit fresh acquisition uses the detailed contributor export, direct links,
  existing retry/resume acquisition and hashes of both archives. It retains the
  **full** candidate pool plus evidence for 90 days.
- The new regression suite is also included in grading CI.

The current old experiment artifact declaration retains reports and a bounded
selection preview, not the full Tatoeba candidate inputs. Supply census artifacts
retain metrics/ranks. These cannot reconstruct discarded uncapped source candidates.
Prefer an actual previously saved full input if available; do not mistake aggregate
counts, selected pack rows or a mixed experiment's rank space for that input.

Local fixtures prove tool behaviour, not full corpus coverage or selected-material
quality. `completeInputScan=true` means EOF of supplied files only;
`publicationSafe=false` remains explicit. Part 7's full pinned inputs/material
review and later selection/freeze gates remain open. No fresh acquisition was run
and no owner request for a long corpus workflow was made in this round.

## Verification and application lane

All 30 available Python check commands exited 0:

- The 13 repository commands from round 01: text/localization, synthetic fixture
  reproducibility, grading/full grading, optimizer, design parity, palettes, NSIS,
  palette previews, Android CI self-test/DEX names and Hot Reload source contracts.
- All 17 `tools/catalog/test_*.py` scripts, including the 11 new admitted-pool
  tests. They cover full provenance retention, missing pairs without filling,
  global identity versus memberships, plain/gzip input equivalence, reproducible
  output, rejected origins/registry/version/reference pins, SHA mismatch,
  out-of-scope records, invalid UTF-8/empty inputs, path aliases and CLI output.
- New workflow YAML parses; every shell block passes `bash -n` and embedded Python
  compiles. Offline execution verifies seven invalid preflight cases, exact artifact
  lookup, missing/duplicate candidates and the explicit fresh choice. This is not
  a remote Actions/artifact-transfer test.
- An offline direct-pair fixture passes ingestion -> full admitted pool -> existing
  Part 10 selection and the v2 builder. Output hashes agree and selection remains
  non-publishing. Fixture limits (`functionTop=0`, `minDeck=1`, `maxDeck=20`) are
  test-only and do not change production defaults or source policy.

The first-round source contracts pass again (38 design and 8 palette checks).
No further Kotlin/UI defect was established by these checks, so no speculative
UI refactor was added. The preinstalled Java executable still cannot load
`libjli.so`; Gradle/kotlinc remain unavailable. No additional tooling was installed.
Local JVM/build/UI verification therefore remains unavailable; the partial remote
result above is recorded separately.

## Original delivery and parallel handoff

Changed existing files: `CHANGELOG.md`, `.github/workflows/grading.yml`,
`docs/CATALOGUE-V2.md`, `docs/PLAN-0.12.md`, `docs/modern_PLAN-0.12.md`.

Added files: `.github/workflows/catalogue-v2-everyday-pool.yml`,
`tools/catalog/everyday_pool.py`, `tools/catalog/test_everyday_pool.py`,
`docs/EVERYDAY-POOL.md`, `docs/ROUND-0.12-02.md`.

At the original delivery, GLM's allowed README/CONTRIBUTING/DESKTOP/HOT-RELOAD
files and its two new documents were untouched. Its returned ZIP was to be compared
against the same round 01 baseline and this round's tree before integration below.
Build versions, signing identity, schema history and binary assets remain the
accepted baseline. Full ZIP packaging verified the 543-file source manifest plus
five explicit additions (548 files), source/ZIP byte agreement and ZIP integrity.
Comparison with round 01 confirms exactly five scoped existing-file edits, five
additions and no deletions; no test outputs, corpora or local Git metadata enter
the delivered source archive.

## Reviewed GLM integration — 2026-10-06

The owner requested one complete merged source ZIP for a subsequent commit.
The returned GLM archive has 545 files: exactly three documentation edits and
two additions against the 543-file round-01 baseline, with no deletions. Those
edits do not overlap the original round-02 changes. Every source byte in the
working tree matched the delivered round-02 ZIP before integration.

Accepted the release asset names, historical port-time labels, 714 translation
keys/seven languages, Hot Reload path repairs and active-cycle wording after
checking the owning source. README required no edit. Added the documentation map
and linked it from CONTRIBUTING; the map includes this round and EVERYDAY-POOL.
Updated the active plan's handoff status and changelog.

Targeted corrections to GLM's evidence:

- Linux build self-tests include a conditional national-locale pass that can
  explicitly skip. The release workflow has the default-locale pass only.
- The skill helpers exist under their respective skill roots; the reported
  missing-helper defect is rejected. Both source skills remain byte-identical.
- Segmentation uses system `icui18n` through `ctypes`, not PyICU. GLM's original
  missing-engine result remains a limitation of its reported Windows environment.
- External execution claims are labelled as GLM-reported. The documentation map
  distinguishes maintained English contracts from original-language historical
  records, without rewriting those records.

Independent checks on the merged tree passed: text, localization, Hot Reload,
NSIS, design parity (38 tests), palettes (8 tests), Android DEX names, segmentation
(16 tests), v1/v2 parity and sharded census equivalence (10 command exits of 0).
The three ICU-dependent scripts unavailable to GLM all pass here. All relative
file links in the seven reviewed entry/plan/round documents resolve. No test or
product code changed during integration; original round-02 code, workflows,
versions, signing identity, schemas and binary assets remain byte-identical.
No Gradle/Kotlin builds, real-platform UI checks or remote workflows were run
for this documentation merge; prior acceptance limitations remain open.

Merged packaging uses the complete 548-file round-02 source manifest plus the
two explicit GLM document additions: 550 source files, no deletions. The packager
requires exact source/ZIP bytes and integrity; no archives, logs, build outputs
or local Git metadata belong to this manifest.
