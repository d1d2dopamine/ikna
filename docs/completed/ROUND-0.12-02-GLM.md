# 0.12 documentation round 02 (GLM) — 2026-10-06

Owner-selected documentation and audit round, run with `ikna-development` and
`ikna-owner-workflow` on top of the complete `ikna-0.12-press-fix-round-01.zip`
snapshot (543 files, verified byte-identical baseline before editing). Active
owners: [PLAN-0.12.md](PLAN-0.12.md) and [modern_PLAN-0.12.md](modern_PLAN-0.12.md).
Scope was limited to `README.md`, `CONTRIBUTING.md`, `docs/DESKTOP.md`,
`docs/HOT-RELOAD.md` plus two new documents; no code, build files, workflows,
schemas, versions, signing material or plans were touched.

This describes GLM's original scope. The reviewer corrected the evidence and
integrated the documents with the parallel round; independent merge checks are
recorded in [ROUND-0.12-02.md](ROUND-0.12-02.md).

## Changed and added files

- `docs/DESKTOP.md` — corrected release asset names, self-test count, a
  non-existent feature, a stale version example, and two stale measurements;
  historical port-time numbers are now labelled as such.
- `docs/HOT-RELOAD.md` — three Windows path samples used `\\` inside code
  spans/fences; Markdown renders backslashes literally, so they displayed
  doubled. Single backslashes now match `tools/dev-hot-reload.ps1`
  (`Join-Path $env:LOCALAPPDATA "Ikna\dev-hot-reload"`).
- `CONTRIBUTING.md` — one stale cycle reference.
- `docs/DOCUMENTATION.md` — **new**: documentation map (ownership, active vs
  historical material, facts requiring re-verification; relative links).
- `docs/ROUND-0.12-02-GLM.md` — **new**: this report.

`README.md` was audited and needed no changes; every claim checked against the
tree held (see below).

## Repairs and evidence

| Document | Error | Evidence | Fix |
| --- | --- | --- | --- |
| DESKTOP.md §Build | "release.yml attaches `ikna-<tag>-windows-x64.zip` and the required `ikna-<tag>-windows-x64-setup.exe`" | `.github/workflows/release.yml`: Windows job produces fixed names `out/ikna-windows-x64-portable.zip` / `out/ikna-windows-x64-setup.exe`; explicit workflow comment: "The file name is fixed rather than derived from the tag, so the README download links resolve to every release" | States the fixed names and the fixed-name rule |
| DESKTOP.md §Linux "What you download" | `ikna-<version>-linux-x86_64.AppImage`; `chmod +x ikna-v0.10.0-press-linux-x86_64.AppImage` | release.yml builds `out/ikna-linux-x86_64.AppImage` and publishes `out/*.AppImage`; fixed-name comment applies to all release files; `v0.10.0-press` also predates the shipped 0.11.0 | `ikna-linux-x86_64.AppImage` in both places |
| DESKTOP.md §Linux "Build" | "release.yml … attaching `ikna-<tag>-linux-x86_64.AppImage`" | Same release.yml steps as above | `ikna-linux-x86_64.AppImage` |
| DESKTOP.md §Linux "Build" | "starts the packaged file once with `--selftest`" | `.github/workflows/build.yml` Linux job runs the AppImage default-locale self test, then a conditional `ru_RU.UTF-8` pass (`locale-gen`, `LANG`/`LC_ALL`, skip message if the locale cannot be generated) | Default pass plus a second locale pass that explicitly skips when unavailable |
| DESKTOP.md §Stage 3 | "along with tray notifications" | No `SystemTray`/`TrayIcon`/tray implementation anywhere in `desktop/`, `shared/` or `app/` sources; the same document's §"The answer" defers a tray icon to a later stage | Tray mention removed; the implemented Stage 3 items remain |
| DESKTOP.md §Build wrinkle | "The app can show `0.10.0 press`, while `desktop/build.gradle.kts` uses `0.10.0`" | `app/build.gradle.kts`: `appVersionName = "0.11.0 press"`; `desktop/build.gradle.kts`: `packageVersion = "0.11.0"` | Example updated to 0.11.0 |
| DESKTOP.md §"What the app is actually made of" | Port-time tree measurement ("103 Kotlin files, 29,141 lines" over `app/src/main/java/dev/ikna`) stated in the present tense | Current tree: `app/src/main/java` holds 46 Kotlin files / 12,358 lines; the portable majority lives in `shared/src/jvmShared` (114 .kt / 24,628 lines including tests) | Measurement labelled "at port time", with a pointer to `shared/src/jvmShared`; numbers kept as historical evidence |
| DESKTOP.md same section | "568 keys per table" | `tools/check_localization.py` reports **714 keys each**; counted with the checker's own regex; the 18-key gap vs a naive lowercase-only count comes from digit-bearing prefixes such as `a11y.*` | 714 |
| DESKTOP.md §Windows title bar | "names in all six interface languages" | 7 language tables (`StringsEn/Ru/Pl/Es/Fr/De/Pt.kt`); the `pc.017`–`pc.020` title-bar keys exist in all seven | "all seven" |
| HOT-RELOAD.md | `%LOCALAPPDATA%\\Ikna\\dev-hot-reload\\…` in two code blocks and one inline code span | Markdown code renders backslashes literally → doubled backslashes on screen; `tools/dev-hot-reload.ps1` uses single backslashes | Single backslashes (3 spots) |
| CONTRIBUTING.md §UI changes | "The 0.11 cycle is not a visual redesign" | CONTRIBUTING's own `ikna-active-plan: docs/PLAN-0.12.md` marker; the 0.11 cycle has shipped ([PLAN-0.12.md](PLAN-0.12.md)) | "The active cycle is not a visual redesign" |

### Claims audited and confirmed correct (no edit)

- README: release download file names match release.yml outputs; `minSdk 29`
  = Android 10+; arm64/`-Pikna.abi=legacy32` split (`armeabi-v7a`) exists; the
  300 MB Anki rejection limit exists (localisation key `anki.022` in all seven
  tables); seven interface languages; twelve palettes in two lightings
  ([DESIGN.md](DESIGN.md), enforced by `check_palettes.py`); beginner en→ru
  pack fetched into `app/src/main/assets/packs/` by
  `tools/catalog/fetch-bundled-pack.sh`; all relative links resolve.
- CONTRIBUTING: all listed check scripts exist; JDK 17 + Gradle 8.10.2 match
  the workflows; no wrapper jar is committed (verified); `-Pikna.unsigned=true`
  and `-Pikna.abi=emulator` (→ x86_64) are real; Catalogue defaults match the
  workflow (`max_deck` 8000, `contexts_per_target` 3, morphology on,
  phonetics off, `publish` off, fixed `catalog` tag); the census cross-check
  `--dir catalog-v2 --expect-build catalog-v2/BUILD.json` is what
  `catalogue-v2.yml` runs; `CATALOGUE-V2-READINESS.md`/`.json` is the name of
  the readiness report the census workflow generates from
  `tools/catalog/readiness_audit.py`, not a missing repository document.
- DESKTOP: title strip 45 dp / buttons 44 dp (`WINDOWS_TITLE_BAR_HEIGHT = 45`);
  two-pane threshold (900 dp in `Shell.kt`, 220 dp deck column); installer
  paths `%LOCALAPPDATA%\Programs\ikna`, `/S`, `/PURGE=1`,
  `.ikna-install-root` in `desktop/installer/ikna.nsi`; `appimagetool` 1.9.1
  pin and `C.UTF-8`/`LD_BIND_NOW`/`IKNA_*` env contracts in
  `tools/appimage/build-appimage.sh`; decks installed before Compose
  (`Main.kt` `runBlocking { container.install() }` →
  `DesktopContainer.install()` → `packLoader.installBundledPacks()`, with the
  database opened before the window); wordmark two-tint masks exist for both
  platforms (`shared/src/androidMain/res/drawable-nodpi/ikna_wordmark*.png`,
  `shared/src/desktopMain/resources/drawable/…`, `ic_notification` used by
  `work/ReminderWorker.kt`).
- HOT-RELOAD: Gradle 8.10.2 bootstrap, `%LOCALAPPDATA%\Ikna\dev-hot-reload`
  tools/logs/profile layout, `IKNA_HOME_OVERRIDE` Developer-Sandbox default,
  `-UseRealData`, wait-list for `settings.gradle.kts` + build files + desktop
  entry point, `:desktop:hotRun --auto`, timestamped logs, PowerShell
  `-NoExit` — all match `dev-hot-reload.cmd` + `tools/dev-hot-reload.ps1`.

## Verification reported by GLM (its machine)

Environment: Windows 10 (10.0.26200), Python 3.12.10, JDK 17.0.20 installed.
No Gradle distribution (see below). GLM reports that all commands ran
sequentially from the repository root. This section records the external
report, not an independent reproduction of its Windows session; the merge
review and local verification are recorded in [ROUND-0.12-02.md](ROUND-0.12-02.md).

Repository checks — **all pass**:

- `python tools/check_text.py` — 485 text files, 7 localisation tables OK
  (re-run after the new documents were added: still OK).
- `python tools/check_localization.py` — 7 languages, 714 keys each; registry,
  fallback, tokens, labels and selectors agree.
- `python tools/check_hot_reload.py` — PASS (plugin 1.1.1, `:desktop:hotRun
  --auto`, Gradle bootstrap 8.10.2, isolated Developer Mode, `-NoExit`).
- `python tools/check_design_parity.py` — 38 source contracts, OK.
- `python tools/check_palettes.py` — 8 palette contracts, OK.
- `python tools/check_nsis.py` — NSIS contract OK.
- `python tools/check_optimizer.py` — 9 checks OK.
- `python tools/check_grading.py` — 10 checks OK.
- `python tools/grading/generate_synthetic.py --check` — 3 × 1206 synthetic
  rows reproducible.
- `python tools/grading/check_full.py` — 8 checks OK.
- `python tools/make_palette_preview.py --check` — agrees with all 24 palette
  combinations.
- `python tools/check_android_ci.py --self-test` — 10 tests OK, 1 skipped:
  the shell-wrapper test requires Bash and skips with a stated reason on
  Windows (the by-design skip path from round 01's IKNA-T-002 repair).
- `python tools/check_android_ci.py` — DEX-safe identifiers: 157 Android-bound
  files pass.

Catalogue contract tests — 13 of 16 exit 0: `test_catalogue_v2_contract`,
`test_ingestion`, `test_morphology`, `test_build_catalogue_v2`,
`test_meta_info` (10 tests), `test_readiness_audit` (5 tests),
`test_storage_experiment`, `test_supply_census`,
`test_wikimatrix_stream_status`, `test_everyday_rebuild`,
`test_knowledge_rebuild`, `test_selection_policy`,
`test_globalvoices_attribution`.

Markdown link check over the four owned documents and the two new ones: all
relative link targets resolve; README's `#ikna`/`#русский` anchors exist.

Packaging: full source ZIP built per `ikna-owner-workflow` with this archive
as the authoritative manifest — 543 baseline files byte-verified (only the
three edited documents differ), the two new documents added explicitly, no
deletions; the finished archive holds 545 entries and passes ZIP integrity and
per-file byte agreement against the tree, with both skills and
`ikna.keystore` (byte-identical) included; ignored machine-local clutter
(`__pycache__/`, produced by running the Python checks) is excluded. Output
written outside the source tree.

## Verification not run, and why

- **Gradle/Kotlin builds and tests** (`:shared:desktopTest`,
  `:shared:testDebugUnitTest`, `testReleaseUnitTest`, desktop builds): no
  `gradle` executable exists on this machine and no wrapper jar is committed;
  installing tooling was not permitted. JDK 17 is present but cannot replace
  the pinned Gradle 8.10.2 distribution.
- **System-ICU-dependent catalogue tests** — `test_segmentation`,
  `test_v1_v2_parity`, `test_supply_census_shards` abort with
  `IcuUnavailable: CJK segmentation needs ICU`; system ICU was unavailable
  to this process and installing tools was not permitted. Merge review
  corrected GLM's PyICU diagnosis: `tools/catalog/segmentation.py` loads
  `icui18n` directly through standard-library `ctypes`, not the PyICU package. `test_segmentation` still passes
  its 5 non-ICU tests; 11 error out on the missing engine.
- **Real-platform checks**: Android device/emulator, packaged Windows/Linux
  desktop runs, widget/launcher behaviour and screen-reader announcements —
  no such platform is reachable from this session.

These results are static/source evidence only; they do not substitute for the
Kotlin suites, builds or the platform smoke list in
[ROUND-0.12-01.md](ROUND-0.12-01.md), which remain open for the toolchain
owner.

## Limited UI/localisation audit — findings left for the other developer

Report-only (outside the allowed files); nothing was changed for these. Each
item separates evidence from inference.

1. **Missing-helper finding rejected during merge review.** GLM looked for
   `scripts/` at the repository root. The references are relative to each skill:
   `skills/ikna-development/scripts/preflight.py` and
   `skills/ikna-owner-workflow/scripts/package_repo.py`. Both are present in
   the round-01 baseline and returned archives. The reviewer ran preflight
   successfully; no skill change is needed.
2. **Historical language convention clarified during merge review.**
   `ai-audits.md`, `rele.md` and part of `test-cli.md` are dated Russian
   evidence records. DOCUMENTATION.md now distinguishes English maintained
   contracts/runbooks from historical records retained in their original
   language. No historical report was translated or rewritten.
3. **Localisation heuristic found no actionable defects** (recorded as a
   clean result, not a defect): all seven tables have the same 714 keys, no
   `{token}` mismatches (enforced by `check_localization.py`), no format
   differences; values identical to English in other languages are brand,
   palette or same-spelling terms (`AUTO`, `BETA`, `NEUTRAL`, `PHOSPHOR`,
   `PORTUGUÊS (BRASIL)`, `GEOLOGICA`, French `Animations`/`Vibration`/`Rare`,
   German `Vibration`/`SYSTEM`), i.e. intentional. No hard-coded
   user-visible `Text("…")` literals were found in desktop panes or shared UI
   sources.
