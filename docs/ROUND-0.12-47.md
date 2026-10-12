# Round 47: surface deck/settings restore failures

2026-10-12. Cumulative on Round 46. A narrow continuation of restore reliability:
exceptions while installing a bundle deck or applying decoded settings must not
be translated into an apparently successful bundle result.

## Scope

The bundle reader previously caught exceptions from each deck import and from
SettingsBackup.apply. It could then return BUNDLE with fewer decks/settings=false,
which the existing backup pane treated as successful completion. Round45 fixed
the corresponding journal failure; this stage closes the two remaining operational
exception paths.

Keep intentional compatibility behavior: blank entries skip, settings that decode
to null (including synthetic snapshots) skip, future compatible settings versions
remain accepted, malformed individual deck rows use the existing parser policy.
No deck format, settings format, parser, schema or catalogue change is included.

There is still no overall bundle transaction. Earlier decks, journal data, chunks written inside a failing local deck import,
or individual settings edits can be committed before a later step fails. The UI's
existing restore-failure message deliberately does not say that nothing changed.
Cancellation must propagate as cancellation rather than a successful skip.

## Separate known limitation

A nonblank all-invalid deck file can still create an empty pack and count as a
returned deck import under the existing importer contract. Correcting that policy
is separate from preserving operational exceptions and is not silently bundled
into this patch.

## Verification

Regression evidence uses temporary homes, a disposable SQLite write-failure
trigger and settings storage/callback failures. The baseline had four failures out of seven tests; the final focused run passes
19 tests (seven new and twelve selected prior regression tests), zero failures,
errors or skips. The initial fixture-only compile correction is retained
separately and was fixed before the successful baseline reproduction. No real learner data or backup is used. Android/Windows
runtime acceptance remains outside the available Linux JVM environment.

Final full verification: **69 shared + 176 desktop tests**, zero failures/errors/
skips; Linux release packaging succeeds. Static checks pass: 43 design contracts,
778 keys in seven locales, UTF-8/NFC. No additional headless profile run was
needed for this two-wrapper change; Round46's 11-process evidence remains
historical evidence, not a new Round47 runtime claim.

The final finite source review found no missing Round42 source files; README,
signing identity, all eight Room schemas, catalogue tools and workflows are
unchanged. Historical evidence remains preserved. The source-package manifest
is regenerated against the actual full Round46 archive at packaging time.
