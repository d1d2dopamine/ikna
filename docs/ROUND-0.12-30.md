# Round 30 — full saved-input historical control

2026-10-12, Asia/Yekaterinburg. The saved pool was processed completely under
explicit historical cap 8000 with ICU 74.2 and the unchanged selection policy.
This is a non-publishing diagnostic, not the current cap-12000 scenario.

## Acceptance and exact output

All 11,099,490 unique candidates, 5,290,947 contexts and 110 directed pairs were
processed. Independent streaming gzip CRC, logical SHA-256, compressed SHA-256
and line-count checks reproduce the historical output exactly:

- 928,162 included target memberships, 2,094,192 contexts, 2,094,728 origins.
- 328 included decks: 257 normal, 71 thin; 2 omitted. The pre-omission report total
  is 928,224 and must not be confused with serialized included memberships.
- Logical SHA-256: `7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4`.
- Compressed SHA-256: `a2a5e4bb1e4079e88c8986443c95761795d5fe66ec14ee46a1f83e099e95df64`.
- 255,938,165 compressed / 1,512,254,677 logical bytes.

Stage counts, quality counters, complete deck inventory and summary match the
historical report. Seven differences are recorded exhaustively: five source-file
hashes and Python/zlib versions. No other report differences exist. This confirms
this output, not universal equivalence for every input. Local Python 3.12.14 and
zlib 1.3.2 differ from historical 3.12.15 / 1.3. Original historical ICU binary
hashes were unavailable; current mapped ICU binaries are pinned in the profile.

## Full stage measurement

One first-process run: 8335.166 seconds measured selection elapsed, including
stage instrumentation and durable boundary snapshots. Supervisor interval:
2026-10-11T21:54:24Z through 2026-10-12T00:13:22Z. Setup/integrity work outside the
measured selection explains the difference. No repeated full-run distribution or
OS-cold-cache claim is made.

| Stage | Calls | Wall seconds | Process CPU seconds |
| --- | ---: | ---: | ---: |
| verification | 1 | 138.010 | 138.982 |
| segmentation_setup | 1 | 0.165 | 0.165 |
| staging | 1 | 342.062 | 335.108 |
| ranks | 11 | 125.799 | 75.008 |
| context_analysis | 11 | 239.194 | 238.936 |
| pair_evidence | 110 | 7187.008 | 6853.630 |
| allocation | 330 | 249.734 | 250.351 |
| selected_output | 330 | 43.303 | 43.595 |
| selected_output_finalize | 1 | 0.237 | 0.236 |
| postselection_verification | 1 | 0.445 | 0.443 |
| diagnostic_output | 1 | 0.185 | 0.186 |

Pair evidence accounts for 86.23% of measured elapsed time;
context analysis accounts for 2.87%. Thus the earlier
bounded analysis-cache savings do not identify the dominant full-input cost.
Production caching remains off. A bounded exact dense-pair function profile is
the next justified diagnostic before selecting any further optimization.

Process lifetime high-water RSS was 1,666,994,176 bytes; the separate 1-second
guard's sampled peak was 1,666,170,880 bytes. Guard limits were 4 GiB sampled RSS,
5 GiB address space, 2 GiB available memory and 4 GiB free disk. No limit fired.
Stage RSS is sampled whole-process memory, not incremental stage allocations.
Completed SQLite staging is 9,586,688,000 bytes. Large staging, source corpus and
full selected output are retained locally and intentionally excluded from the
source delivery ZIP; all identities are recorded in compact evidence.

The host was shared during limited earlier intervals recorded in SHARED-HOST.json;
filesystem caches were uncontrolled. Sampling can miss transient peaks and hidden
cgroup limits were unavailable. There is no claim of an exclusive or statistically
stable benchmark, full-cache benefit, semantic translation accuracy, publication
acceptance or application visual verification.

## Evidence and continuation

See [selection-round30](evidence/selection-round30/) for complete stage records,
source/runtime/input bindings, resource guard outcome, all historical differences
and independent full-output verification. The comparison helper expects the
original results directory layout and large finalized files; it is evidence code,
not a self-contained corpus download or a bypass of successful-run gates.

The unchanged full-material structural audit passed: all 928,162 memberships,
221,653 unique global targets, 2,094,192 contexts and 2,094,728 origins satisfy the
existing automatic structural/provenance/boundary contracts. Audit wall time was
151.14 seconds, sampled peak RSS 301,072,384 bytes, no resource stop. Its explicitly
labelled current-self-baseline retains all 3,280 preview memberships with zero
context changes; this comparison is self-consistency only, not historical preview
or semantic quality gain. Historical identity was verified independently above.
`semanticAccuracyCertified`, `materialReviewCompleted` and `contentFrozen` remain
false. Compact audit scope, report and guard evidence are included.

The bounded dense-pair diagnostic is reported in the next checkpoint. Earlier reports remain historical
checkpoints. No application code, visuals, schema, source admission or default
selection cap changed in this round.

## Offline classifier archive follow-up

The approved read-only inspection of one official en→de archive is now recorded
in [CLASSIFIER-ARCHIVE-AUDIT-0.12.md](CLASSIFIER-ARCHIVE-AUDIT-0.12.md), with a dated
follow-up to the earlier feasibility decision. Its local SHA-256, complete safe
inventory, inert metadata and publisher notice are retained. No model was run,
installed or redistributed. Underlying text-source provenance and independent
quality/runtime evidence remain unresolved; retain existing rules.
