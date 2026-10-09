# Round 12 — successful Everyday handoff and coverage diagnosis

Recorded 2026-10-08, Asia/Yekaterinburg. Documentation/evidence round only;
application behavior, selection policy, source admission and publication are unchanged.

## Successful saved-pool run

The owner supplied the small quality artifact from
[37674241928](https://github.com/d1d2dopamine/ikna/actions/runs/37674241928).
It supersedes the pending corrected-run status after failed run `37647495700`.
Selection reused the admitted pool from `37458524156`, source version
`2026-10-03-2d4eee105639ba24`; no new acquisition or admission is claimed.
Policy `boundary-diversity-v2` completed with `qualityGatePassed=true` and
`automaticChecksCompleted=true`. No previously included directed pair was lost.

| Measure | Result |
| --- | ---: |
| Languages / directed pairs | 11 / 110 |
| Planned pair/level combinations | 330 |
| Included decks | 328: 257 normal, 71 thin |
| Omitted decks | 2 |
| Unique global learning targets | 221,653 |
| Included target/deck memberships | 928,162 |
| Retained contexts / provenance origins | 2,094,192 / 2,094,728 |
| Pre-omission selection total | 928,224 |

The 62-entry difference is exactly the two omitted decks (30 and 32), not missing
output. Targets shared across decks and alternate contexts are not extra learner
memories. `publish`/`publish-thin` are selection classifications, not publication.
All three levels here are catalogue frequency bands, not retired exercise modes
or certified CEFR levels. This is Everyday only, not a completed v2 catalogue.

The Japanese guard deferred 210,164 morpheme-cut choices and 1,047 out-of-vocabulary
boundary choices; canonical ambiguity deferred 11 choices. These are choice-entry
counters, not unique bad sentences or a corpus defect rate. The known exact source
quarantine excluded 8 candidate rows. 41,419 near-duplicate alternative contexts
were suppressed. The 868,539 budget rejections are not all low-quality material.
1,424 similar remaining alternate pairs and short CJK-target counters are
informational, not gate failures.

The old preview retained 3,255 of 3,280 memberships; 25 were absent and 2,055 of
the retained memberships had changed contexts. This comparison covers only the
old preview, not a complete identity diff of the old catalogue.

## What causes the holes

Full report inventory and all 73 below-threshold decks are preserved in
[COVERAGE.md](evidence/everyday-round12/COVERAGE.md) and
[COVERAGE.json](evidence/everyday-round12/COVERAGE.json).

- 53 of 73 already have fewer than 1,000 eligible targets after the source/token
  sieve. More useful source evidence is required to reach that diagnostic threshold
  under the unchanged sieve, even before allocating distinct contexts.
- 20 have at least 1,000 eligible targets but fall below 1,000 during context
  allocation. These need an allocation/evidence investigation before attributing
  their entire shortfall to the corpus.
- None of the 73 loses a target to the 8,000 budget. Raising the cap cannot fill them.
- 48 involve Korean on one side; other weak coverage includes Polish/Portuguese,
  Polish/Chinese and Italian/Polish. All 110 direct pairs exist in this pinned pool.

The two omissions are **Polish studied with Korean meanings**, middle and advanced.
Their pair contains only 94 candidate rows; 21 fail source-sentence length and one
has no usable target. There are 210 eligible exact targets across levels:

| Level | Eligible | Context-allocation loss | Selected | Included |
| --- | ---: | ---: | ---: | --- |
| beginner | 117 | 66 | 51 | yes, thin |
| middle | 46 | 14 | 32 | no, below 40 |
| advanced | 47 | 17 | 30 | no, below 40 |

Thus the immediate omission combines low direct source supply, distinct-context
allocation and the minimum deck size. It is not zero source supply or a Japanese
guard problem. The reverse direction uses the same 94 pair rows but different
learning-language tokens/ranks and retains 55/42/45 entries.

The selector reserves both primary and alternate contexts to one target within
each deck. It allocates greedily and retains bounded evidence per target. Collision
counts describe current policy losses; they do not prove that every loss is
unavoidable, that a maximum matching would recover it, or that there is a code bug.
No raw-pool/complete-material bytes were available locally to test those alternatives.
Do not silently relax uniqueness or remove alternate contexts to raise counts.

## Recorded owner decisions and remaining work

The owner does not assess languages they do not know and does not perform an
exhaustive catalogue review. The earlier viewer/owner-review proposal is superseded;
round 10 was the last routine manual-selection round. Continue with automated,
bounded checks and later stages. High quality remains required, while structural
checks do not certify every translation.

**Open-corpus replenishment is needed if broader Everyday coverage is a product
requirement.** The current snapshot cannot supply 1,000 eligible targets in 53
weak decks under its current sieve; 1,000 is a thinness marker, not a mandatory
publication quota. For the 20 allocation-limited decks, diagnose policy/evidence
first. A small, useful deck may remain small; no claim is made that every direction
can or must reach equal size.

The owner authorizes documenting the need now and defers corpus selection. The
future task must measure **net new eligible targets and distinct natural contexts
in the measured weak directions/levels**, not corpus headline size. Priority is
Polish–Korean, then the remaining low-supply Korean directions and other measured
weak pairs. Consider a demonstrably fuller/newer Tatoeba input or another suitable
open corpus later; neither is assumed to close the gaps. Check direct alignments,
Everyday suitability, redistribution/attribution, reproducible input identity and
quality before admission. No synthetic sentences or machine-translation pivots.

MASSIVE remains excluded; WikiMatrix expansion remains unadmitted. Knowledge/World
cannot be relabelled as Everyday filler. Their source/provenance gates are separate.

Next stages:

1. Preserve the complete selected material from this exact successful run; do not
   reacquire or repeat selection just to recover these report counts.
2. Finish Part 11 Everyday census/snapshot using that full file, including explicit
   exclusions and an accepted quality/coverage profile. This report audit starts
   the coverage inventory; it is not a complete content freeze.
3. Investigate bounded context-allocation cases as a separately scoped task; record
   recoverable losses before proposing any selection-policy change. If selection
   changes later, create a new identity/snapshot rather than altering this evidence.
4. Evaluate replenishment corpora later against this inventory. Admission and any
   new acquisition/build are separate tasks, not performed by this round.
5. Measure lossless storage and validate client acceptance in Part 12 after the
   content decision. World completeness and final publication remain open.

## Retention and verification

The full selected artifact is `catalogue-v2-everyday-selected-material` from
`37674241928`: gzip SHA-256
`a2a5e4bb1e4079e88c8986443c95761795d5fe66ec14ee46a1f83e099e95df64`,
255,938,165 bytes; logical SHA-256
`7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4`,
1,512,254,677 bytes. It is absent from the small attachment; its local retention
by the owner has not been confirmed for this new run. The previously saved raw
pool is a different artifact and cannot substitute for the selected output.

Exact selection/quality/pool report bytes are saved losslessly as `.json.gz` under
`docs/evidence/everyday-round12/`. The machine inventory binds both supplied ZIPs,
report hashes, source/pool pins, selected-file identity and all 330 deck rows.
Local checks: ZIP integrity, bound report hashes, preview decoding/count, unique
pair/deck inventory, per-deck and per-pair balances, aggregate totals and omitted
count reconciliation. Repository text and documentation-link checks pass; source
ZIP manifest/bytes/integrity are verified during packaging. No workflow/build was
run and the large files were not independently reread locally.

`semanticAccuracyCertified`, `materialReviewCompleted`, `contentFrozen` and
`publicationSafe` remain false. Provided-input EOF is not proof of all possible
Tatoeba corpus/export coverage. No global freeze or publication approval is given.
