# Round 22 — selection-engine planning checkpoint

2026-10-09, Asia/Yekaterinburg. Authorized scope: update plans and deliver a complete
source ZIP for the owner's commit; stop work afterward. No implementation starts.
Baseline: round-21 full source ZIP, 665 files, SHA-256
`765cdbbbd9ded3ac36158bc4a9138aadcd70bac22ef8015c5cf49c2543781014`.
Every baseline member matched before editing; no Git checkout or installation.

## Recorded work

- New [SELECTION-ENGINE-PLAN-0.12.md](SELECTION-ENGINE-PLAN-0.12.md): stage profiling,
  dependency-bound reuse, separate content scores, classifier alternatives,
  independent bounded evaluation and determinism requirements.
- Update [PLAN-0.12.md](PLAN-0.12.md), [CATALOGUE-V2.md](CATALOGUE-V2.md) and
  [CORPUS-CANDIDATES-0.12.md](CORPUS-CANDIDATES-0.12.md) to agree on next-step order.
- Preserve prior research/evidence, accepted selections and the 12,000 ceiling.
  No classifier choice, source admission, app/version/schema, skill, workflow or
  catalogue asset changes. Full assembly/publication remain deferred.

## Verification and limitations

Repository preflight; human-text/localization checks; local document links;
baseline diff/scope and whitespace review; full ZIP source-manifest agreement,
byte verification and integrity. Application builds and large corpus workflows
are unnecessary for these documentation-only edits and were not run.
The read-only code observations are not a measured runtime profile; no classifier
training, inference comparison, speedup or quality result is asserted.

## Handoff

The ZIP is cumulative and ready for the owner's source commit. No new workflow
result is requested tonight. Resume later from the active selection-engine plan,
starting with bounded profiling of retained data; keep source replenishment and
current-data validation in scope without beginning final Catalogue assembly.
