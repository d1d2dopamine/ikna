# Round 10 fix 1 — canonical occurrence ambiguity

## Observed failure

Owner supplied the traceback for run
[37647495700](https://github.com/d1d2dopamine/ikna/actions/runs/37647495700).
The saved run metadata shows commit `bee52bb5e9c92d438a41c50b5697bdd018679d49`,
successful input/dependency/regression steps, and failure in selection after
2 hours 3 minutes 18 seconds with exit code 1. Complete output upload and evaluation
were skipped. The only artifact was the 476-byte input-reference report; there is
no saved completed selection to resume. The 240-minute timeout was not reached.
The error was `ValueError: Japanese choice is not one complete ICU token`.

## Root cause and correction

`phrase_choices` preserves legacy `.lower()` occurrence counting, whereas global
target identity and the Japanese boundary guard use NFKC/casefold. Distinct source
spellings such as `Ａ`/`A`, `ß`/`SS`, and `カ`/`ｶ` therefore passed the older sieve
but resolved to multiple occurrences of one canonical identity. The new guard
incorrectly treated this valid source ambiguity as a tooling error. The exact
failed corpus sentence was not included in the traceback; these are reproducing
fixtures, not claimed corpus observations.

Quality policy v2 now counts complete source tokens by canonical identity before
boundary analysis in every learning language. An ambiguous choice is deferred
with `canonical-target-ambiguous-deferred`; other eligible targets in the same
context survive. The direct Japanese guard applies the same rule. No occurrence
is chosen arbitrarily, no text/ID/rank is rewritten, and original candidates stay
in the admitted pool. A uniquely occurring target may still use another context.
The legacy selector stays available unchanged. The counter counts rejected
choice entries, not unique source sentences or a corpus defect percentage.

Missing tokens, invalid analyzer ranges, missing source coverage, morpheme cuts,
source quarantine, complete-material contracts and publication gates remain
strict. True analysis failures now name the collection/language, context hash and
bounded source text. Japanese analysis runs immediately after staging, before
selecting other languages. Logs report staging progress, context analysis and
pair transitions. This is diagnostic progress, not a resumable checkpoint.

## Verification and next action

Thirteen quality tests pass with the actual pinned Sudachi analyzer/dictionary.
They include the formerly crashing width/casefold/kana cases, an emoji/UTF-16
case, German canonical ambiguity, and refusal of a forged partial-token choice.
The admitted-pool integration runs seven Japanese contexts through selection and
complete-material evaluation; eight ambiguous choice entries are deferred and
the automatic gate passes. Existing identity, selection, segmentation and
provenance checks are recorded in
[everyday-round10-fix1.json](evidence/everyday-round10-fix1.json).
These are local fixtures, not a successful full-corpus result or semantic certificate.
No app, version, Room schema, build dependency, signing asset or skill change occurs.

Commit the complete fix ZIP and start a **new dispatch** of
**catalogue v2 everyday quality handoff** on the updated branch, with unchanged
prefilled input pins. Do not use Re-run jobs on the old failing commit. It reuses
the existing admitted pool and baseline, without acquisition or admission rebuild.
A full selection must run once because the failed run saved none. Send the small
quality-report artifact and retain the large selected-material artifact. After
success, proceed to Part 11 census/content snapshot and Part 12 storage/client
validation; no new routine manual-selection round is added.
