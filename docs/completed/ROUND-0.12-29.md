# Round 29 — guarded full-input diagnostic readiness

2026-10-12, Asia/Yekaterinburg. This checkpoint prepares the measured next full
saved-input run; it contains no completed full selection result. The earlier
bounded cache prototype remains disabled in the normal selector.

## Explicit scope and resource boundaries

`selection_profile.py --input-scope full-saved-input --measurement-mode stages`
now has an honest full-supplied-input diagnostic status, always non-publishing.
The default 64 MiB compressed-input guard remains. Starting the 545,718,435-byte
saved pool requires an explicit `--max-input-bytes` override. That byte check is
not a decompressed-size, memory or duration guarantee.

`selection_profile_guard.py` supervises only its own Python selection child. The
planned/default ceilings are 4 GiB sampled RSS and 5 GiB virtual address space,
with at least 2 GiB available memory and 4 GiB free disk. RLIMIT_AS is installed
before heavy selection imports. RSS/headroom/disk are sampled each second and
can overshoot between polls; this is not a hard host-memory guarantee. Required
Linux process/host/disk measurements fail closed if unavailable. This host has no
visible cgroup memory-limit files, so hidden limits are explicitly unknown.

Guard output and profile output must be new, disjoint directories on the same
monitored filesystem. SQLite/Python temporary directories are pinned to a new
guard-owned `tmp` directory on that filesystem. No user file is removed to gain
headroom. SIGTERM and exceptions enter owned-child cleanup; the previous signal
handler is restored. Linux parent-death signaling plus a parent-ID check prevents
the selection child from running unguarded after abrupt supervisor death. A
supervisor SIGKILL leaves its last evidence snapshot incomplete and adoption/reaping
of the terminated child to the operating system.

## Durable diagnostic evidence, not a resume engine

Each stage boundary atomically saves detached completed metrics and active-stage
names. A completed source-validation stage therefore remains observable if later
staging fails. `PROFILE.partial.json` preserves available input/implementation pins
and completed runs; the guard separately retains RESOURCE.json and process logs.
A complete PROFILE.json is written only after successful output and identity checks.
Partial SQLite/selected files are not trusted or resumable. Retry requires a new
output directory and repeats mandatory validation and staging.

Checkpoint JSON serialization/fsync occurs outside the proxied stage windows but
inside total admitted-selection elapsed. This instrumentation is disclosed; do not
call its timing a baseline engine comparison with earlier checkpoint-free runs.
Snapshot data describes sampled whole-process memory, not exclusive allocations.

## Review and verification

Independent preflight review caught two real safety hazards before a large run:
SIGTERM could bypass supervisor cleanup, and SQLite spill files could use `/tmp`
on another filesystem. Both were fixed and covered by actual subprocess tests,
including supervisor SIGTERM/SIGKILL behavior and temporary-directory placement.
The final focused metrics/profile/guard suite has 40 passing tests; the broader
pinned-runtime cache/selection/segmentation/audit suite has 126 passing tests.
No full input was used for these fixture tests. Source text and localization
contracts and original application/build/signing identity checks remain clean.

## Historical control contract and runtime correction

The retained accepted Part 10 report used an 8,000-target cap, not the current
12,000 ceiling/default. The first full performance replay will explicitly use
`--max-deck 8000` to compare with that historical control. It will not replace the
current default or silently present an 8,000 scenario as the current 12,000 policy.
The expected selected logical SHA-256 is
`7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4`, representing
928,162 included memberships, 2,094,192 contexts and 2,094,728 origin occurrences.
All output remains diagnostic until comparison actually succeeds.

Correction to round 25 wording: historical Part 10 records Python 3.12.15 / zlib
1.3; this host uses Python 3.12.14 / zlib 1.3.2. ICU 74.2 and Sudachi version/
dictionary match, but historical ICU binary hashes are unavailable. Exact historical
runtime reproduction is not asserted. Logical selected-output identity is the
primary comparison; compressed-byte identity and report/environment differences
must be reported separately. The source round-25 report now includes this explicit
correction; previously delivered archives remain historical checkpoints.

See `evidence/selection-round29/` for tests, frozen invocation intent, historical
control pins and resource preflight. The next result must say whether the full
run completed, failed or was stopped by a resource bound; readiness is not success.
