# 0.11 corpus decision record — Parts 6, 8 and 9

This record freezes the conservative decisions taken from the completed 0.11
source experiments before the final Catalogue v2 census.  A larger candidate
pool is not an acceptance criterion: publication safety wins over deck size.

## Part 6 — MASSIVE / Everyday

**0.11 decision: do not admit MASSIVE to production.**  Tatoeba remains the
production Everyday source for the 0.11 freeze.  MASSIVE stays registered as
`candidate`, so the normal builder continues to reject it.

The successful evidence run showed that MASSIVE has substantial quantitative
value: 802,174 eligible target memberships, 464,641 memberships not supplied by
the Tatoeba baseline, and about 4.48 million possible additional contexts.  The
deterministic samples also exposed a semantic problem that the first source gate
did not model: MASSIVE is an NLU *localization* corpus, so slot values may be
deliberately changed to locally appropriate people, places, artists or services.
For example, retained samples paired Chicago with Incheon and Shakira with a
different Korean singer.  Those are legitimate localizations for intent/slot
training, but they are not safe literal bilingual contexts for ikna.

The experimental adapter is tightened in this commit for future re-evaluation:
localized rows now require two positive `slots_score` judgments, internally
consistent `annot_utt`/`slot_method` metadata, and reject any slot whose upstream
method is `localization`.  The original unjudged `en-US` SLURP seed remains
allowed through the source-specific gate.  This stricter experiment does **not**
overturn the 0.11 decision without a new reviewed evidence run.

## Part 8 — WikiMatrix direct-pair Knowledge expansion

**0.11 decision: do not admit the all-direct-pair expansion.**  The already
approved production WikiMatrix scope remains available; the new 55-physical-pair
expansion stays behind the explicit `review` policy and cannot become production
material by score threshold alone.

The direct-pair experiment proved that supply is not the problem: every planned
physical pair exists and the candidate pool can fill the intended Knowledge
budget.  Quality is the blocker.  After replacing the historical 1.04 floor with
pair-specific 1.10/1.11/1.12 review floors, deterministic retained samples still
contained obvious semantic mismatches.  Examples include French “Son fils Yahyâ
ben Yahyâ … lui succéda.” aligned to German “Jakob und sein Herr: Hörspiel.” and
multiple German/Korean rows that remain unrelated even above 1.12 (one sampled
mismatch scored 1.234).

Therefore no automatic score retuning is treated as evidence of correctness.
The pair rules remain `review`, preserving the fail-closed behavior.  Direct-pair
expansion can return after 0.11 with an independent semantic-quality signal or a
new audited source; it is not allowed to delay the 0.11 content freeze by lowering
quality requirements.

## Part 9 — Global Voices / World

**0.11 decision: continue, but only through verified record attribution.**  The
native XCES run resolved 752,051 of 752,052 aligned rows to the actual OPUS XML
sentence ids, so native alignment identity is considered solved.  The remaining
blocker is document-level article attribution.

This commit adds `globalvoices_manifest.py`.  An OPUS document name may propose
one date/slug Global Voices URL, but that candidate enters the manifest only when
the live page is fetched, redirects stay inside `globalvoices.org`, a canonical
Global Voices URL is recovered, and at least one credited contributor is found in
page metadata/markup.  Any failure remains unresolved and its rows are filtered
out by the existing attribution gate.  The workflow is bounded by default for a
diagnostic run and can later be run with `manifest_max_documents = 0` for the
full evidence build.

## Gate into Part 11

Parts 6 and 8 are now conservative 0.11 decisions rather than open requests to
make noisy corpora fit.  Part 11 may use Tatoeba Everyday and the existing
approved WikiMatrix Knowledge scope.  World enters the freeze only after the new
Part 9 live-manifest workflow demonstrates non-zero, auditable attribution; if it
cannot, World remains fail-closed rather than receiving synthetic or
corpus-level attribution.
