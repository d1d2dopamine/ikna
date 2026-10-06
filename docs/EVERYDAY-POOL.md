# Admitted Everyday pool — Part 7

The active [0.12 plan](PLAN-0.12.md) retains the reviewed decision: Tatoeba is
the admitted Everyday source; MASSIVE is excluded. The existing
`everyday_rebuild.py` and Everyday experiment workflow remain Part 6 comparison
tools. Their combined ranks and preview are not the final Tatoeba-only pool.

`tools/catalog/everyday_pool.py` consumes **local normalized candidate files**.
It validates every origin against the audited Tatoeba policy and one explicitly
pinned source version, exact-deduplicates candidates while retaining all origins,
and saves the complete requested candidate scope. There are no network requests,
deck assets, publication steps, synthetic filler or changes to target identity.

The report records SHA-256/size for each input and the pool, logical JSONL identity,
registry/pipeline hashes, Python/zlib/segmentation evidence, source version, scope,
duplicates and rows outside scope. Every requested directed pair appears, including
zero-supply pairs, with eligible targets by level and sieve rejection counters.
Global exact target count is separate from summed pair/level memberships.

Coverage uses the existing builder's primary-origin context and shortest preferred
meaning rule. Alternative merged origins and other meanings remain in the full
pool for later selection. Eligible target-context memberships are pre-selection
evidence, not retained contexts or extra learner cards. Deterministic samples are
review aids, never a replacement for the full pool.

Identical ordered input bytes, scope, pipeline and segmentation environment yield
identical pool bytes; gzip has no timestamp or filename. Input order is pinned by
the report because first-seen order still matters for frequency ties and primary
origins. A complete input scan means EOF of the supplied files. It does **not**
prove that an arbitrary small file represents the entire Tatoeba export.
`sourceAdmissionPassed=true` therefore does not set `publicationSafe=true`:
acquisition completeness, selected-material review, freeze and release gates remain.

## Local reuse

Use a previously retained **complete normalized candidate file**, its source
version and digest. Do not feed selected pack rows, aggregate census reports or
an experimental mixed-source pool to this tool.

```bash
python3 tools/catalog/everyday_pool.py \
  --candidates tatoeba.jsonl.gz --source-version "$TATOEBA_VERSION" \
  --expect-sha256 "$TATOEBA_SHA256" \
  --pool everyday-candidates.jsonl.gz --staging everyday-pool.sqlite3 \
  --json EVERYDAY-POOL.json --markdown EVERYDAY-POOL.md \
  --samples EVERYDAY-SAMPLES.md
```

The version/digest variables identify actual retained data, not placeholders.
`--learn` and `--meanings` default to all supported content languages;
`--expect-sha256` accepts one digest per input in the supplied order. Validation
rejects mixed/unadmitted origins, foreign collections, moving version labels,
invalid references, mismatched pins and input/output path aliases. A bad input
does not replace the previous successful pool.

## Workflow without implicit reacquisition

The manual `catalogue v2 everyday admitted pool` workflow defaults to `artifact`:
provide the exact run ID, artifact name, candidate basename, source version and
candidate SHA-256. It downloads that artifact only; a missing or expired artifact
fails without falling back to a corpus download or a latest-run lookup.

`fresh-tatoeba` is a separate explicit input choice for genuinely missing final
inputs. It acquires the detailed sentence export with contributor metadata and
direct links through the existing retry/resume helper. Both archive digests and
Last-Modified values are recorded; the source version binds the sentence export
date to a hash of both inputs. This workflow was not dispatched during round 02.

The artifact `catalogue-v2-everyday-admitted-pool` contains the full
`everyday-candidates.jsonl.gz`, `EVERYDAY-POOL.{json,md}`, review samples and
`INPUT-REFERENCE.json`, retained for 90 days. For the next reuse, take the version
and `pool.sha256` from that report. Preserve a reviewed snapshot before its Actions
retention expires. With `run_selection=true` (the default), the same workflow
then performs the admitted Part 10 review described below. Pool upload happens
**before** selection, so a failed selection can reuse this run's version/hash
in artifact mode rather than downloading the corpus again.

## Admitted Part 10 review

`tools/catalog/everyday_selection.py` consumes the full pool and its exact
`EVERYDAY-POOL.json`. It verifies raw/logical hashes, sizes/counts, the admitted
Part 7 status and registry pin, then checks every candidate/origin against the
current Tatoeba policy, source version and recorded language scope. Report and
pool changes during the run fail closed. Duplicate candidates contradicting the
report are rejected; input/report/registry aliases cannot be overwritten.

Selection uses precisely the report's learning/meaning scope and function-word
sieve. Every directed pair and level appears, including zero-source omissions.
Reports separate eligible/selected/budget/context-collision counts. No shortage
is padded; exact target identity and provenance remain unchanged.

```bash
python3 tools/catalog/everyday_selection.py \
  --pool everyday-candidates.jsonl.gz --pool-report EVERYDAY-POOL.json \
  --staging everyday-selection.sqlite3 \
  --json EVERYDAY-SELECTION.json --markdown EVERYDAY-SELECTION.md \
  --preview everyday-selection-preview.jsonl.gz \
  --samples EVERYDAY-SELECTED-SAMPLES.md
```

The workflow retains `catalogue-v2-everyday-selection-review` for 90 days:
selection JSON/Markdown, selected-material samples, bounded JSONL preview,
the bound pool report and input reference. The full pool is in the separate
same-run `catalogue-v2-everyday-admitted-pool` artifact. Defaults remain
8,000 maximum targets per level, 40 minimum, 1,000 thin-deck threshold and
three contexts per target. Review rows are limited to the first ten policy-ranked
targets per publishable deck; they are not full selected assets or representative
quality statistics. Contributor metadata remains in preview origins and samples.
Native-speaker/reliability or translation correctness is not established by
passing this automated gate. Human review/freeze/publication remain open.

Generic selection now rejects input/output aliases (including hard links),
fails on damaged UTF-8, and writes deterministic gzip bytes without output-name
or timestamp metadata. The admitted wrapper also retains a previous staging file
if validation/selection fails. No public Catalogue asset/index is generated.

## Next owner run (0.12 round 08)

The 2026-10-06 public API checkpoint lists no retained final admitted-pool
artifact, and the old Everyday experiment lists no artifacts. Its declaration
had not retained full candidates. Local fixtures are not production inputs.
Full final Tatoeba input therefore remains unavailable to this session.

After committing the complete round-08 ZIP to the default branch, open Actions →
**catalogue v2 everyday admitted pool** → Run workflow. If no complete normalized
input was separately saved, choose `input_mode=fresh-tatoeba`, keep both language
lists at their eleven-language defaults, and leave `run_selection=true`.
Artifact-specific run/version/hash fields are unused in this mode. This obtains
the missing final admitted input, not another Part 5 census or MASSIVE admission
experiment. No World or Knowledge experiment is requested for this round.

If a complete candidate input is already available in an Actions artifact,
prefer `artifact` with its exact run/name/basename/version/hash. Missing artifacts
still fail without a fresh fallback. Send the resulting run link plus both
artifacts (or at least `EVERYDAY-POOL.json`, `EVERYDAY-SELECTION.json`,
`EVERYDAY-SELECTED-SAMPLES.md` and `INPUT-REFERENCE.json` for initial review).
Preserve the full pool locally; aggregate reports/previews cannot recreate it.
If selection fails, the prior pool-upload step determines whether the saved
input can be reused. See [ROUND-0.12-08.md](ROUND-0.12-08.md) for evidence limits.

The old Everyday experiment currently retains reports and a bounded selection
preview; the supply census retains metrics/ranks, not raw candidates. Those outputs
cannot reconstruct the final uncapped pool. Existing run evidence is catalogued
in [ROUND-0.12-02.md](ROUND-0.12-02.md); completed admission decisions stay closed.
