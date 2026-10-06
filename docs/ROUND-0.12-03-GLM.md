# 0.12 small UX round 03 (GLM) — 2026-10-06

Owner-selected small UX round with reported Windows launch/compile attempts, run with
`ikna-development` + `ikna-owner-workflow`. Base: `ikna-0.12-press-round-02-merged.zip`,
550 files, SHA-256 `fb01fc3f2dd86c0caf66488fa20e07da166bfa6dbf8de697b3aeeab7f88f9c07`
(verified with `certutil -hashfile` before unpacking; the tree was unpacked fresh and
byte-compared against that archive during packaging). No owner-local files were
overwritten: the working directory is a fresh unpack, and the pre-existing workspace
folders belong to earlier rounds of this same collaboration.

Allowed scope respected: `SearchPane.kt`, `BackupPane.kt`, the one `HOT-RELOAD.md`
path error, and this report. Nothing else in the tree changed — no versions, schemas,
signing, shared UI, plans, CHANGELOG, workflows or tests. No commit/push, no remote
workflows, no new Git metadata, no tooling installed.

Reviewer note: this document records GLM's external execution report. Only the
source ZIP was supplied to Codex; the referenced evidence directory/logs were
not attached. No independent Windows timing, compile or process verification is
claimed. Codex accepted the busy guard, removed an unintended UTF-8 BOM and
completed the related Search width-limit repair during integration; see
[ROUND-0.12-03.md](ROUND-0.12-03.md) for the final tree and local checks.

## Changed files

1. `desktop/src/main/kotlin/dev/ikna/desktop/SearchPane.kt` — one guard line plus a
   two-line comment inside `search()`.
2. `docs/HOT-RELOAD.md` — Data safety inline path now uses single backslashes.
3. `docs/ROUND-0.12-03-GLM.md` — **new**, this report.

`BackupPane.kt` is unchanged: nothing was reproduced against it (see the blocked
section), and the round rules allow leaving it untouched in that case.

## REP-1 — Search: keyboard path bypassed the busy guard (SearchPane.kt)

- **Symptom / root (source-confirmed).** The Search button is `enabled = !busy && …`,
  but `search()` itself checked only text validity (`localSearchTerms(query) == null`),
  and the `BasicTextField.onPreviewKeyEvent` handler for `Key.Enter`/`Key.NumPadEnter`
  called `search()` directly. Pressing Enter while a query was running therefore
  launched a second concurrent `deckRepository.search(...)`.
- **Established effect.** The token check (`if (token != request) return@launch`)
  keeps results correct — the older coroutine's output is discarded — but the duplicate
  DB query runs, and `busy` now ends when the *newest* request ends. This is a
  confirmed source-path divergence between the button and keyboard contracts.
- **Not established at runtime.** This session had no UI access (see blocked
  section); no interactive overlap or query-duration measurement was established.
  The defect is source-confirmed, runtime-intersection unverified.
- **Fix (single root, minimal).** `search()` now begins with
  `if (busy || localSearchTerms(query) == null) return`. Both paths honour the same
  contract; token/request invalidation is untouched; editing the text still resets
  `busy`/`token` so a new valid query is never blocked; after completion or failure
  (`busy = false` in the coroutine) Enter works again. No artificial delays added.
- **Regression test.** No existing test covers this screen (`desktop/src/test`
  contains window-chrome, hotkey, sandbox and simulation tests only). Suggested for
  Codex, in the owner's files: a desktop JVM test with a recording fake
  `deckRepository` asserting that a second `search()` while `busy == true` launches no
  second repository call, and that after `busy` resets a new query launches. Not
  added here because test files and any shared fake fixtures are outside this round's
  allowed files.

## DOC-1 — HOT-RELOAD.md Data safety path

The remaining inline path `%LOCALAPPDATA%\\Ikna\\dev-hot-reload\\profile` rendered
with doubled backslashes; it now matches `tools/dev-hot-reload.ps1`
(`Join-Path $env:LOCALAPPDATA "Ikna\dev-hot-reload"`). This was the one path sample
missed by the round 02 documentation repair.

## BackupPane review — no change

Source review of the presentation paths: success/error/path lines are ordinary
wrapping `Text` composites; `busy` disables all three buttons and also guards the
window drop-import; both file dialogs return `null` on cancel before any busy state is
set; size rendering (`megabytesOf`) is plain text. Focus indication exists at source
level (`IknaWideButton` thickens its own border when focused). Without runtime access
nothing was reproduced, so per the round rules the file stays byte-identical.
Left as observations for Codex: (a) whether plain `Modifier.clickable` buttons
activate on Enter/Space on desktop (focus ring is confirmed in source, activation is
not verified); (b) long-unbroken file-name rendering under large fonts — flagged, not
reproduced, no CJK/long fixture available in this session.

## Runtime session on this PC — what was actually observed

All times below are GLM-reported local times, 2026-10-06. GLM reports copying
logs next to the ZIP (`ikna-round-03-evidence/`), but that directory was not
included in the supplied attachment. No screenshots exist because the session
had no screen access — none are claimed.

- **Launcher.** `dev-hot-reload.cmd` started at **01:58:34** against the unpacked
  base in `ikna-round-03`, default profile (no `-UseRealData`), one session only.
  The launcher's own bootstrap reused the already-downloaded pinned Gradle 8.10.2 in
  `%LOCALAPPDATA%\Ikna\dev-hot-reload\tools` (present from the owner's earlier
  sessions) — this was a **warm** start; the cold first-launch time was not observed
  here.
- **App start.** The application started at **02:00:45** (2 min 11 s after launcher
  start; warm) on the isolated **DEVELOPER** sandbox (`profile/ikna-data-profile` =
  `DEVELOPER`; the normal desktop learner database was never opened). JetBrains
  Runtime 21, Compose Hot Reload 1.1.1, supervised log
  `hot-reload-2026-10-06_01-58-34.log`.
- **Auto-reload did not fire.** `SearchPane.kt` was saved at **02:02:47** and again at
  **02:07:24**. Within ~10 minutes no continuous Gradle daemon process or daemon log
  appeared, no `desktop/build` file changed after the edit, and the supervisor log
  stayed at its startup banner. (The owner's own earlier session logs also show that
  reload activity never reaches the supervisor log, so the silent tee-log alone would
  prove nothing.) This is GLM's reported observation; without the referenced
  logs/process records the reviewer cannot confirm the cause or whether an
  automatic watcher failed to start. GLM reports that the app kept running the
  base classes and no error was produced.
- **Compile verification of the fix.** Per the round rules no second Gradle session
  was started while the hot session lived. After stopping my own session (its four
  JVM processes, stopped by known PID; nothing else touched), **one** sequential,
  memory-bounded compile was run with the **same pinned distribution**:
  `tools\gradle-8.10.2\bin\gradle.bat --no-daemon --console=plain --max-workers=1
  -Dorg.gradle.jvmargs=-Xmx768m -Dkotlin.daemon.jvm.options=-Xmx512m
  :desktop:compileKotlin` → **BUILD SUCCESSFUL in 31 s**. This proves the fix
  compiles; it is not a reload and not a UI check.
- **The visual matrix (task A) and interactive Search/Backup checks — BLOCKED, not
  run.** The Computer Use UI-automation runtime is unavailable in this agent session
  (`Computer Use is unavailable for this node_repl session`), so there was no
  pointer, keyboard or screen access: DARK→GREY sequences, angular/rounded and
  animation on/off combinations, hover/parked-pointer/Tab-focus/scroll frame checks,
  window-size and font-scale matrices, dialog cancellation and the Enter-during-busy
  runtime reproduction could not be exercised. They remain open exactly as listed in
  [modern_PLAN-0.12.md](modern_PLAN-0.12.md) (Desktop D / Visual character J / Fast
  loop L). No Signal Frame, Browse or Settings defect can be claimed from this
  session — none was observed, none was reproduced.

## Repository checks reported by GLM (exit 0)

- `python tools/check_text.py` — 491 text files, 7 localisation tables OK.
- `python tools/check_localization.py` — 7 languages, 714 keys each; registry,
  fallback, tokens, labels, selectors agree.
- `python tools/check_hot_reload.py` — PASS (launcher contract, pinned 1.1.1/8.10.2
  unchanged; documentation edit keeps it).
- `python tools/check_design_parity.py` — 38 source contracts OK.
- `python tools/check_palettes.py` — 8 palette contracts OK.
- `python tools/check_android_ci.py` — DEX-safe identifiers, 157 Android-bound files.
- `python skills/ikna-development/scripts/preflight.py <repo>` — sanity report:
  versionName 0.11.0 press, desktop 0.11.0, Room schema 10, active plan
  `docs/PLAN-0.12.md` (read-only; not build evidence).

## Not run / blocked (summary)

- JVM test suites, Android/desktop applications builds, installers: not run (no
  second Gradle session beyond the single focused compile above; no toolchain beyond
  the pinned distribution).
- Auto-reload verification: attempted and **not observed to fire** in this session —
  recorded as an environment finding for the Hot Reload tooling owner, with no
  repository file changed in response and no tooling bypassed.
- All interactive UI checks (both allowed panes and the untouchable shared surfaces):
  blocked by missing UI access, as described above.

## Hand-off to Codex

1. The Search busy-guard fix needs its focused regression test (description above)
   in the owner's test files, plus the runtime Enter-during-busy reproduction once
   UI access exists.
2. Reported auto-reload non-firing needs diagnosis from the original launcher,
   compiler and process records; its cause is not established. Nothing in the
   launcher contracts changed, and `check_hot_reload.py` passes.
3. BackupPane: keyboard-activation question and long-name/large-font rendering
   remain open; no defect was established.

## Packaging — original GLM delivery

Full source ZIP built with `skills/ikna-owner-workflow/scripts/package_repo.py`,
using the merged round-02 archive as the authoritative source manifest and
`--add docs/ROUND-0.12-03-GLM.md`: manifest agreement, per-file byte verification
(exactly `SearchPane.kt` and `docs/HOT-RELOAD.md` differ from the baseline, both
intentional), no deletions, ZIP integrity checked; both skills, `ikna.keystore`,
schemas and all binary assets byte-preserved. Output written outside the source
tree; evidence files are delivered beside it, not inside the archive.

At original delivery this diff awaited review and integration. Source review and
merge are now complete, with reviewer corrections described above. This record
does not claim that 0.12 or the integrated runtime is verified; the common-tree
compilation and visual pass remain open.
