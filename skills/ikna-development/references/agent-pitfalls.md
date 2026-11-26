# Recurring AI Agent Pitfalls in ikna

This reference captures concrete failure modes observed in real development
sessions on `ikna`. Treat every item here as a mandatory pre-commit guardrail,
especially when full local Gradle builds are skipped in favor of fast static
checks and GitHub Actions CI.

## 1. Never guess symbol, property, or Compose API names

Agents repeatedly introduced avoidable CI compile breaks by coding from memory:

- Calling `ReviewRecord.fromEntity(...)` or `.encode()` instead of the real
  `ReviewRecord.of(...)` and `ReviewRecord.json.encodeToString(ReviewRecord.serializer(), ...)`.
- Referencing non-existent fields on `IknaSettings` (such as
  `settings.fsrsPersonalizedWeights`) when optimizer state lives on
  `container.optimizer.state.value.usingOptimized`.
- Writing `verticalArrangement = Alignment.Bottom` instead of `Arrangement.Bottom`.

**Rule:** Before using any symbol, extension, or property you did not already
read in this session, inspect its declaration with `read_file` or `git grep`.

## 2. Run a repo-wide `git grep` after every move, split, or rename

When splitting monoliths (`LearningRepository.kt`, `SettingsScreen.kt`,
`RestoreRepository.kt`), agents moved helper methods into collaborator services
but left dangling call sites inside the original class (for example,
`optionalHardCandidates(...)` left behind in `LearningRepository.inspectSession`).

**Rule:** Immediately after moving, renaming, or deleting any function, field,
or composable, run `git grep -n "<symbol>" -- shared/ app/ desktop/` and verify
every remaining call site and import across all three modules.

## 3. Respect text-based Source-Contract Hotspots before refactoring

`ikna` enforces critical invariants via static Python and Java tests that inspect
the raw source text of specific files. Moving a checked expression into another
file will fail CI even when Kotlin compiles cleanly.

Hotspot files and their contract checkers:

- `shared/src/jvmShared/kotlin/dev/ikna/data/repo/LearningRepository.kt` ->
  `tools/check_grading.py`, `tools/grading/check_full.py`,
  `tools/check_optimizer.py`, `tools/check_design_parity.py`
- `app/src/main/java/dev/ikna/ui/settings/SettingsScreen.kt` ->
  `app/src/test/java/dev/ikna/ui/SettingsSourceContracts.java`,
  `tools/check_design_parity.py`, `tools/check_localization.py`,
  `tools/check_optimizer.py`, `app/src/test/java/dev/ikna/ui/nav/RouteContinuityTest.kt`
- `shared/src/jvmShared/kotlin/dev/ikna/data/repo/RestoreRepository.kt` &
  `SchedulerMigration.kt` & `JournalReplayEngine.kt` ->
  `tools/grading/check_full.py`, `tools/check_optimizer.py`
- `shared/src/jvmShared/kotlin/dev/ikna/ui/session/CardStack.kt` &
  `desktop/src/main/kotlin/dev/ikna/desktop/SessionPane.kt` ->
  `tools/check_grading.py`, `tools/check_design_parity.py`

**Rule:** Before refactoring any hotspot file, run
`git grep -n "<FileName>" -- tools/ app/src/test/` so you know which lines must
remain in the facade or which contract test must be updated intentionally.

## 4. Kotlin / JVM / Android DEX and Compose traps

- **DEX-safe test names:** Never put `.` (such as `w17..w19`) or illegal
  bytecode characters inside backtick Kotlin test names. Android DEX and JVM
  tooling reject them; run `python tools/check_android_ci.py` before pushing.
- **All 7 locales must stay symmetric:** Every new UI string key must be added
  to all 7 files (`StringsEn`, `StringsRu`, `StringsDe`, `StringsEs`,
  `StringsFr`, `StringsPl`, `StringsPt`) in the same edit, verified with
  `python tools/check_localization.py`.
- **Reactive localization inside `remember`:** `S.t(...)` reads Compose state
  (`S.lang`, `S.pseudo`). Any `remember(...)` block that calls `S.t(...)`
  directly or through a helper must include `S.lang, S.pseudo` (or the resolved
  strings) in its keys, otherwise switching interface language leaves stale text.
- **Live DB observation on stats/profile screens:** Screens showing study counts
  must observe Room `Flow` / `StudyStatsRepository.observeChanges()` and
  `container.sessionChanges.generation`, not load only once in
  `LaunchedEffect(reload)`. Do not reset `snapshot = null` on background refresh
  to avoid flicker.
- **Test arithmetic against real defaults:** When writing threshold tests (e.g.,
  governor overheat accuracy drop, FSRS intervals, timeout limits in
  `PairedLanTransportTest`), check the actual constant in source (`0.15`,
  `SOCKET_MS = 5_000`, etc.) instead of assuming a convenient number.

## 5. LAN Sync & Networking failure modes

Real phone-to-desktop sync testing surfaced four recurring bug classes that unit
tests on `127.0.0.1` alone can miss:

- **VPN & virtual adapters:** Active VPNs (WireGuard, Clash/sing-box/Nekoray,
  AmneziaWG, Tailscale, Windows TUN, WSL/Hyper-V) can hijack LAN traffic or sort
  ahead of physical Wi-Fi. Always classify/rank physical LAN interfaces first,
  carry multi-host fallback addresses, and preserve direct physical-LAN socket
  binding (`ConnectivityManager` non-VPN Wi-Fi/Ethernet on Android; same-subnet
  physical NIC bind on desktop) with clean fallback to default routing.
- **Clock skew between devices:** Never reject valid invitations or proposals
  strictly due to small device clock drift without explicit tolerance handling.
- **QR payload density:** Keep QR invitations compact so phone cameras decode
  reliably on real screens.
- **Cancelled/stale proposal recovery:** Echoed or cancelled
  `ProfileConsentProtocol` proposals must recover cleanly instead of deadlocking
  repeated pairing attempts.
- **Honest diagnostics without secret leaks:** Never silently swallow transport
  or protocol errors, and never expose certificates, secrets, credentials, or
  raw peer addresses in logs or `toString()`.

## 6. Product & Design boundaries

- **No gamification or streaks:** Never introduce streaks, XP, points, leaderboards,
  guilt-tripping banners, or duplicate deck/word lists onto the Profile screen.
- **No fabricated evidence:** Never claim a build or test passed unless it
  actually ran. Separate fast static contract checks from full CI compilation.

## 7. CI workflow discipline

- **Use `gh` CLI, not a browser:** Inspect workflow status and failure logs via
  `gh run list` and `gh run view <run-id> --log-failed`.
- **No infinite polling timers:** Never schedule recurring timers or shell sleep
  loops to wait on GitHub Actions runs indefinitely.
- **Windows `gh` keyring note:** On Windows, `gh` stores tokens in Windows
  Credential Manager (`keyring`). Sandboxed agents that cannot read the host
  keyring may see `please run: gh auth login` even when the user is signed in.
  Report the auth error plainly instead of looping or probing credentials.
