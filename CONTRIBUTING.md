# Contributing to ikna

ikna is a local-first language-learning application for Android, Windows and
Linux. Contributions are welcome, but changes need to preserve a few product
constraints that are easy to break accidentally: no account system, no
telemetry, no runtime LLM dependency, deterministic learning history, and a
catalogue whose study material can be traced back to its public source.

This file is the practical entry point for working on the repository. The design
rationale lives in [`docs/`](docs/), while the current 0.11 work checkpoint lives
in [`docs/PLAN-0.11.md`](docs/PLAN-0.11.md).

## 🧭 Start here

Before changing code, read the document closest to the area you are touching:

- application structure: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- product behaviour: [`docs/DESIGN.md`](docs/DESIGN.md)
- learning-engine responsibilities: [`docs/LEARNING-ENGINE.md`](docs/LEARNING-ENGINE.md)
- evidence labels and learning claims: [`docs/SCIENCE.md`](docs/SCIENCE.md)
- governor: [`docs/GOVERNOR.md`](docs/GOVERNOR.md)
- grading: [`docs/GRADING.md`](docs/GRADING.md)
- FSRS fitting: [`docs/FSRS-OPTIMIZER.md`](docs/FSRS-OPTIMIZER.md)
- Catalogue v2: [`docs/CATALOGUE-V2.md`](docs/CATALOGUE-V2.md)
- morphology: [`docs/MORPHOLOGY.md`](docs/MORPHOLOGY.md)
- corpus/licence policy: [`docs/SOURCES.md`](docs/SOURCES.md)
- desktop packaging: [`docs/DESKTOP.md`](docs/DESKTOP.md)
- release/version rules: [`docs/VERSIONS.md`](docs/VERSIONS.md)

For 0.11 specifically, use [`docs/PLAN-0.11.md`](docs/PLAN-0.11.md) as the
operational checklist. `ROADMAP-0.11.md` describes the sequence and intent;
`PLAN-0.11.md` records what is actually done, what is waiting on evidence, and
what blocks release.

## 🗂️ Repository map

The main directories have deliberately different responsibilities:

| Path | What belongs there |
| --- | --- |
| `shared/` | Scheduler, repositories, Room database, settings, shared UI/data logic and code used by both Android and desktop. |
| `app/` | Android application, Android-only integrations, widget, reminder, speech runtime wiring and Android tests. |
| `desktop/` | Desktop application entry point, desktop-only integrations, packaging and desktop tests. |
| `tools/` | Offline build/check scripts, catalogue pipeline, grading fixtures, packaging helpers and CI checks. |
| `docs/` | Design contracts, scientific claims, architecture and release/process documentation. |
| `.github/workflows/` | CI, release and catalogue builds. |
| `app/schemas/` | Committed Room schema history. This is source material for migration checks, not generated clutter. |

A change that is useful on both Android and desktop normally belongs in
`shared/`. Platform-specific UI or APIs stay in `app/` or `desktop/`.

## 🔨 Local build

The project uses JDK 17 and Gradle 8.10.2 in CI. No Gradle wrapper jar is
committed, so either install the pinned Gradle version locally or let GitHub
Actions provide it.

Before the first Android build, fetch the speech runtime and pinned starter deck:

```bash
bash tools/voice/fetch-voice.sh
bash tools/catalog/fetch-bundled-pack.sh
```

Common commands:

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon testReleaseUnitTest
gradle --no-daemon :desktop:test
gradle --no-daemon :desktop:createReleaseDistributable
```

For a clone that should not use the repository signing key:

```bash
gradle --no-daemon assembleRelease -Pikna.unsigned=true
```

The Android CI can also build an x86_64 test APK with
`-Pikna.abi=emulator`; published Android builds remain arm64 and legacy 32-bit
ARM as described in the build files.

## ✅ Checks before a pull request

Run the checks relevant to your change. For a broad change, the repository-level
set is:

```bash
python3 tools/check_text.py
python3 tools/check_localization.py
python3 tools/grading/generate_synthetic.py --check
python3 tools/check_grading.py
python3 tools/grading/check_full.py
python3 tools/check_optimizer.py
python3 tools/check_design_parity.py
python3 tools/check_palettes.py
python3 tools/check_nsis.py
python3 tools/make_palette_preview.py --check
python3 tools/check_android_ci.py --self-test
python3 tools/check_android_ci.py
```

Catalogue changes additionally need:

```bash
python3 tools/catalog/test_segmentation.py
python3 tools/catalog/test_catalogue_v2_contract.py
python3 tools/catalog/test_ingestion.py
python3 tools/catalog/test_morphology.py
python3 tools/catalog/test_build_catalogue_v2.py
python3 tools/catalog/test_v1_v2_parity.py
python3 tools/catalog/test_meta_info.py
```

Do not replace a failing check by weakening the check unless the documented
contract itself changed. If the contract changed, update the documentation and
the test together so the new behaviour is explicit.

## 🧠 Learning-engine boundaries

Do not make one component silently take over another component's job. The 0.11
split is:

- **Scheduler** — when an existing learning state is due.
- **Governor** — how much work may enter the day.
- **Target Policy** — what learning target is selected.
- **Context Policy** — which stored context represents that target now.
- **Transfer Policy** — when an unseen context may be used as a transfer probe.
- **Grading** — what the learner's response means.

These boundaries matter for replay and for scientific interpretation. A context
change must not quietly become a grading change, and a governor decision must not
become a scheduler rule.

## 🃋 Catalogue v2 invariants

Catalogue work has a few rules that are more important than file layout.

### Global targets

A `targetId` belongs to the Catalogue v2 identity space, not to a deck. The same
exact target in one learning language keeps the same identity across collection,
level and meaning-language decks.

A deck is membership, not ownership. Installing two decks that contain the same
target must not create two independent learner memories for that exact target.

### Contexts are not extra scheduling cards

One target membership may carry multiple real source contexts. The first is the
primary context; alternatives are material for later Context/Transfer Policy.
They are not independent FSRS cards merely because they came from different
sentences.

Do not inflate catalogue scale by counting alternative contexts as separate
learning targets. Census reports should keep at least these measures separate:

- unique learning targets;
- target/deck memberships;
- retained contexts;
- unique source contexts.

### Sources and collections

The user-facing collections are categories, not permanent aliases for one corpus:

- `Everyday` — initially Tatoeba;
- `Knowledge` — initially WikiMatrix/Wikipedia-derived material;
- `World` — initially Global Voices once record-level attribution is available.

Study sentences must come from the source corpus. Do not generate filler
sentences to reach a quota.

### Provenance and licences

A source must be registered and audited before ingestion. Unknown, unaudited,
non-commercial-only or no-derivatives inputs must not slip into a production
build by convenience.

Keep source version, URL, licence and attribution data sufficient to audit the
result later. Global Voices material must retain the required canonical article
URL and contributor attribution for every published segment.

### Morphology

Morphology is offline build enrichment. It must not add a runtime model to the
app. Rule version 1 does not change `targetId`. A future morphology-aware target
identity is a separate identity-version change and needs migration/evidence work.

When evidence conflicts, prefer leaving a token unresolved over a false merge.
See [`docs/MORPHOLOGY.md`](docs/MORPHOLOGY.md).

### Publication

The public catalogue uses the fixed GitHub release tag `catalog`. Experimental
v2 builds must keep `publish=false` until their census and sample material have
been reviewed.

When v2 is approved, upload deck assets first and `index.json` last. Rename the
release title to `Catalogue v2` only when actual v2 assets are published.

## 🧪 Building Catalogue v2

The experimental full rebuild is the `catalogue v2 build` workflow. It is
intentionally separate from the application build and can run in parallel.

For a scale/census run, keep publication off. The current working configuration
for 0.11 is up to 8,000 unique targets per collection/level deck, up to three
natural contexts per exact target, morphology enabled and phonetics disabled for
the first census pass. The workflow keeps a conservative lower default for
manual safety, so set the scale input explicitly when a full census is intended.

The build may be much slower than Catalogue v1 because it stages and deduplicates
millions of normalized candidates and keeps global target/context relations.
Long runtime by itself is not a reason to bypass the quality or provenance gates.

The outputs to inspect first are the build summary and `catalogue-meta-info`
report. Do not publish merely because the workflow succeeded.

## 🗄️ Room and migrations

User learning history is more valuable than a clean-looking migration diff.
Whenever the schema changes:

1. increment the Room database version;
2. write an explicit migration;
3. preserve existing review/history values unless the documented migration says otherwise;
4. update/commit the generated schema under `app/schemas/`;
5. add or update migration tests;
6. check both a migrated database and a fresh database reach the same intended schema.

Never use destructive migration as a shortcut for a production schema change.

Catalogue v2 shared targets are represented through explicit deck/target
membership. Removing one deck must not delete a shared target that another
installed deck still uses.

## 🌍 Localisation

User-visible strings belong in the project's localisation system, not as
hard-coded English inside UI code. Run `tools/check_localization.py` after adding
or removing keys.

The Android home-screen widget is a special case: it is rendered in the
launcher's process, so the app publishes already-localised strings into
`RemoteViews`. Keep that boundary rather than querying app data from the widget.

## 🎛️ UI changes

The 0.11 cycle is not a visual redesign. Prefer small fixes that preserve the
existing interaction model.

A few project conventions are intentional:

- no streaks, XP, confetti or praise popups;
- no visible backlog counter that pressures the learner;
- do not add choices when the system can make a defensible choice itself;
- animations must obey the existing animations preference;
- major README sections keep their emoji markers because they are navigation
  landmarks, not decoration.

For Android widgets, test the smallest declared size and at least one common
launcher behaviour. Do not assume a 1-cell widget receives the full theoretical
cell height; launchers subtract their own margins.

## 📦 Dependencies and network behaviour

Avoid adding runtime dependencies when an offline build-time step can do the job.
The application has no server component and should not gain one casually.

Downloads performed by CI should be pinned where practical and robust to
transient failures. Large corpus downloads use retry/resume logic; do not replace
that with a bare one-shot `curl` in a workflow.

User-facing network access remains limited to static files and public source
links described in [`PRIVACY.md`](PRIVACY.md) and [`docs/UPDATES.md`](docs/UPDATES.md).

## 🏷️ Versioning and release

A release name contains a numeric version and an epoch word, for example
`0.10.0 press`; the git tag uses hyphens, for example `v0.10.0-press`.

Do not create a release tag until the build file and tag agree. The release
workflow checks this and publishes Android, Windows and Linux artifacts built
from that exact commit.

Catalogue publication is separate from application release publication. The
catalogue keeps the fixed `catalog` tag.

## 📝 Documentation style

Prefer concrete behaviour before architecture. Say what the user or build system
will observe, then explain the mechanism.

Keep scientific confidence explicit. `SCIENCE.md` uses the labels Established,
Supported, Experimental and Product policy; do not turn a product choice into a
research claim by wording alone.

Major README headings use emoji markers in both English and Russian for quick
visual scanning. Smaller technical headings do not need them.

## 🔍 Pull request checklist

Before asking for review, verify that:

- the change belongs in the layer where it was made;
- Android and desktop implications were considered;
- migrations preserve existing user data;
- catalogue identity/provenance rules still hold if catalogue code changed;
- user-visible text is localised;
- relevant tests and repository checks pass;
- generated Room schemas or other intentionally tracked generated artifacts are committed;
- docs describe any new invariant or changed behaviour;
- no public Catalogue v2 release was replaced merely to test a build.

Small, explainable changes are preferred to broad rewrites. ikna already has a
lot of behaviour that looks simple only because the edge cases have been made
explicit; preserve that property.
