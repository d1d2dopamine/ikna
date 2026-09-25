# 0.11 modernization plan

This is the operational plan for the work that sits between the numbered 0.11
release parts: stabilization, application cleanup, Developer Sandbox hardening,
Browse/read-mode usability, desktop correctness, documentation ownership, AI
contributor support and small visual-character improvements.

It does **not** replace [`PLAN-0.11.md`](PLAN-0.11.md). The main plan owns the
Catalogue/learning/release dependency chain and its release blockers.
`modern_PLAN-0.11.md` owns the modernization work that can run beside that chain
without silently becoming a new `Part 5.1`, `Part 6.1`, and so on.

The default rule is simple: modernization must not change learner-history
semantics, Catalogue identity, corpus policy, scheduling meaning or release
ordering merely because those systems are nearby. If a modernization change
requires such a product decision, move that decision back to its owning document
and treat it as core 0.11 work.

## Status language

- `[x]` - implemented in the current modernization worktree or already established
  by the repository.
- `[ ]` - planned work that still needs implementation and verification.
- `REVIEW AFTER 0.11` - useful debt that is too risky or too broad to pull into the
  release unless it becomes a blocker.

Nothing in this file is automatically a release blocker. A modernization item
becomes one only when the current implementation breaks build correctness, data
safety, platform parity, restore/update compatibility or another explicit 0.11
release contract.

## Modernization ordering

Work in this order unless a real build/data-safety defect requires interruption:

1. Stabilize the current application and Developer Sandbox.
2. Finish Browse/read-mode and desktop correctness.
3. Perform application cleanup and dependency/resource audits.
4. Perform the usability pass without redesigning the product.
5. Repair documentation ownership and add AI-contributor guidance.
6. Add low-risk visual character only after the interface is understandable and
   stable.
7. Leave broad structural refactors until the end unless they directly unblock
   one of the steps above.

## Track A - stabilization and build confidence

The modernization track must first stop producing regressions that make later
cleanup hard to trust.

- [x] Keep Android, shared and desktop changes on one coherent source baseline.
- [x] Preserve the repository signing identity (`ikna.keystore`) in complete
  project archives; never drop tracked files during packaging because they look
  like signing material.
- [x] Keep Developer Mode physically separate from normal learner data.
- [x] Make Developer Mode itself the explicit consent to bypass product
  restrictions; do not require a second hidden/optional override switch.
- [x] Keep production policy verdicts visible as Developer Mode diagnostics even
  when those verdicts do not block access.
- [x] Keep technical failures fail-closed: missing content, database failures,
  failed migrations and broken checks are not product restrictions.
- [ ] Require Android and desktop compilation after every cross-platform UI/API
  signature change, especially callback chains through shell/pane wrappers.
- [ ] Add regression coverage for every Developer Mode entry path that can be
  blocked in production, not only Browse.
- [ ] Confirm a clean real-device/desktop smoke pass before treating the
  modernization branch as stable.

Acceptance: the same source tree builds on both application targets, normal mode
never observes synthetic learner data, and Developer Mode cannot accidentally
fall back to production restrictions because of stale settings.

## Track B - Developer Sandbox hardening

Developer Sandbox is a real Ikna profile with synthetic input data, not a fake
repository implementation.

Already established:

- [x] Separate normal/developer database files.
- [x] Separate normal/developer settings.
- [x] Reuse the production Room schema, DAOs, repositories, Scheduler, Governor
  and policy code.
- [x] Restart when switching profiles so old ViewModels/repositories cannot retain
  the previous data root.
- [x] Mark developer exports as synthetic and reject them from normal restore.
- [x] Avoid normal reminder/daily/export background scheduling in the developer
  profile.
- [x] Provide deterministic seed scenarios for empty, early, mature, Browse-ready,
  statistics-rich and return-after-break states.
- [x] Keep a persistent `DEV` marker visible while the developer profile is active.

Next work:

- [ ] Add a large-backlog scenario.
- [ ] Add low-accuracy and low-activity Governor scenarios.
- [ ] Add overheated/safety-valve scenarios.
- [ ] Add frequently-forgotten-target scenarios.
- [ ] Add an optimizer-ready history scenario with enough real-shaped reviews to
  exercise FSRS fitting surfaces.
- [ ] Add explicit time-of-day scenarios where the production verdict is useful
  to inspect even though DEV access is forced.
- [ ] Later, after Parts 14-16 define stable history contracts, add context-history
  and transfer-ready synthetic scenarios instead of inventing them early.
- [ ] Add a compact Developer diagnostics surface that explains production gates,
  for example plan incomplete, late-night, credits, backlog, history maturity and
  candidate availability.
- [ ] Verify that reseeding only destroys the developer profile and never touches
  normal learner history or normal preferences.

Acceptance: every expensive-to-reproduce product state can be reached
reproducibly without adding `if (developerMode)` shortcuts to product policy.

## Track C - Browse / reading mode

Browse is passive reading, not a second review session.

- [x] Replace left/right card swiping with a vertically scrollable reading feed.
- [x] Show multiple entries in one column so the next item is naturally below the
  current one.
- [x] Keep the original learning-language context and highlight the target/chunk.
- [x] Keep transcription with the learning-language block when available and
  enabled.
- [x] Put translation/meaning in a separate block below the original context.
- [x] Put provenance/source in its own smaller block and keep public-source links
  actionable.
- [x] Remove Browse grading/review gestures and review-session language.
- [x] Count an exposure only after an item actually reaches the viewport; do not
  spend Browse accounting on lazily precomposed but unseen rows.
- [x] Tune spacing, maximum line width and vertical rhythm on both phone and
  desktop using real Catalogue examples rather than synthetic one-line strings.
  Done as the 640dp feed measure, the 26sp titleLarge leading for multi-line
  prompts and a 12dp gap before the provenance block; desktop verified on the
  seed deck's real Tatoeba sentences, phone shares the same constants.
- [ ] Verify long contexts, CJK text, transcription wrapping, large font scales
  and narrow Android windows.
- [ ] Verify keyboard/wheel/touch scrolling parity and focus behaviour on desktop.
- [ ] Keep source-link interaction from accidentally triggering scroll/selection
  side effects.

Acceptance: a user can enter Browse and simply read downward; nothing in the
primary interaction implies that an answer, swipe or grade is required.

## Track D - desktop correctness cleanup

Desktop correctness work stays separate from visual redesign.

- [x] Preserve floating window geometry rather than overwriting it with maximized
  or fullscreen bounds.
- [x] Restore the prior floating placement when leaving fullscreen.
- [x] Keep maximized windows non-draggable while preserving normal floating drag.
- [x] Recover a saved window that is wholly outside the currently attached monitor
  set after a monitor is disconnected.
- [x] Preserve valid negative monitor coordinates for screens left/above the
  primary display.
- [ ] Exercise mixed-DPI multi-monitor restore on a real Windows machine.
- [ ] Recheck minimize/maximize/restore/fullscreen transitions after the off-screen
  recovery logic.
- [ ] Audit keyboard shortcuts, pointer behaviour, drag/drop, tray actions and
  close/save ordering for platform parity and stale-state bugs.
- [ ] Confirm the custom chrome remains usable at minimum supported window sizes.

Acceptance: window state survives ordinary monitor/layout changes without opening
invisible, corrupting saved bounds or changing behaviour between restarts.

## Track E - application cleanup inventory

Do not equate old or rarely executed code with dead code. Classify each candidate
before changing it:

- `DELETE` - proven unused with no entry point, reflection, manifest, workflow,
  serialization, migration or compatibility ownership.
- `KEEP` - live ordinary code.
- `KEEP - CONTRACT` - compatibility/migration/restore/release evidence that may
  look unused but is required by a documented contract.
- `KEEP - EXPERIMENTAL` - intentionally retained experimental path, such as a
  rejected-for-0.11 corpus adapter that remains useful for future evidence.
- `REVIEW AFTER 0.11` - plausible cleanup whose risk is not justified before the
  release.

Audit order:

- [ ] Desktop shell/platform code.
- [ ] Android-only platform code.
- [ ] Gradle dependencies, plugins and resources.
- [ ] Shared UI code.
- [ ] Documentation comments and stale explanatory prose.
- [ ] CI/workflow helpers.
- [ ] Offline tools only after checking Actions/runbook usage.
- [ ] Learning/data code last, because apparently-unused compatibility paths have
  the highest cost if removed incorrectly.

Specific work:

- [x] Remove the first small set of proven private helpers found during the initial
  cleanup pass.
- [ ] Produce a repeatable dead-code inventory rather than doing search-and-delete
  cleanup ad hoc.
- [ ] Remove unused resources only after confirming no Android XML, Compose
  resource lookup, desktop resource load or packaging script references them.
- [ ] Search for duplicate platform implementations that can safely use existing
  shared code, without forcing unrelated architecture changes.
- [ ] Keep Room migrations/schemas, restore/export compatibility, Catalogue parity
  fixtures, decision records and reproduction evidence out of automated deletion.

Acceptance: every deletion has a defensible ownership/caller argument and no
cleanup depends on weakening an existing regression check.

## Track F - dependencies, resources and deprecated APIs

This is a warning/dependency audit, not a target of "zero warnings at any cost".

- [ ] Inventory direct Gradle dependencies by actual source/test/tool usage.
- [ ] Remove dependencies that are demonstrably unused on all relevant source
  sets.
- [ ] Check build plugins and configuration blocks for obsolete ownership.
- [ ] Audit Android resources and desktop assets for true orphans.
- [ ] Replace deprecated Compose clipboard APIs where the current replacement is
  behaviourally equivalent.
- [ ] Review deprecated desktop resource APIs and migrate only when packaging and
  resource lookup remain identical.
- [ ] Review Android deprecated callbacks individually; keep platform-required
  compatibility overrides even when the method itself is deprecated.
- [ ] Re-run packaging/minification checks after dependency or resource removal.

Acceptance: fewer real warnings/dependencies/resources with no behavioural or
compatibility regression hidden behind cleanup.

## Track G - interface usability pass

This is a usability audit, not a redesign. Preserve Ikna's visual language while
making actions and states easier to understand.

- [ ] Review every disabled primary action: the user should be able to understand
  why it is disabled without knowing Ikna internals.
- [ ] Prefer visible disabled controls with a useful explanation over unexplained
  disappearance when the feature concept remains relevant.
- [ ] Review Settings hierarchy and group development, maintenance and destructive
  actions more clearly inside the existing Rare section.
- [ ] Rewrite user-facing internal terminology where names such as Governor,
  policy or component leak through product UI unnecessarily.
- [ ] Audit destructive confirmations for clear scope: normal data, developer data
  or both.
- [ ] Audit Android/desktop wording and interaction parity.
- [x] Add the shared Signal Frame hover/focus treatment to core interactive
  primitives, deck/today entry points and Browse source links so pointer targets
  identify themselves without permanent extra chrome.
- [ ] Audit the remaining direct `clickable` surfaces that bypass shared controls
  and decide which ones should adopt Signal Frame rather than applying it blindly.
- [ ] Audit keyboard and pointer discoverability on desktop beyond the first
  Signal Frame pass.
- [ ] Audit semantic/accessibility labels for important controls.
- [ ] Test smallest Android layouts and large system font scales.
- [ ] Audit shared-target UX so deck membership is never presented as a second
  independent learner memory.

Acceptance: the interface explains itself more often without adding onboarding
walls, gamification or a new visual system.

## Track H - documentation ownership and drift cleanup

The documentation goal is to separate stable contracts from current snapshots.

Core rule:

> If a current fact can be derived from repository source or a deterministic
> machine report, do not maintain a second manual copy of that fact in several
> Markdown files.

- [ ] Add `docs/DOCUMENTATION.md` as the repository contract for editing Markdown
  and deciding where facts belong.
- [ ] Define four document classes: long-lived contract, operational/status,
  decision/evidence and generated/derived report.
- [ ] Make `PLAN-0.11.md` the owner of core 0.11 live status and blockers.
- [x] Add this modernization plan as the owner of parallel stabilization/cleanup/UI
  work.
- [ ] Keep `ROADMAP-0.11.md` focused on rationale and ordering rather than copying
  every live checkpoint from the main plan.
- [ ] Audit `ARCHITECTURE.md`, `DESIGN.md`, `CATALOGUE-V2.md`, `SCIENCE.md`,
  `VOICE.md`, `VERSIONS.md`, `DESKTOP.md`, `README.md` and other contract docs for
  copied current-state counts/versions/defaults.
- [ ] Replace duplicated current values with links to the actual owner wherever a
  visible derived value is not necessary.
- [ ] Use generated Markdown blocks only when a derived value genuinely needs to
  remain visible, and add its deterministic generator/check in the same change.
- [ ] Preserve historical measurements as dated evidence rather than silently
  refreshing them to today's state.

Acceptance: changing an app version, localization count, Catalogue census or
workflow default does not require manually hunting through unrelated contract
docs.

## Track I - AI contributor contract

Repository guidance should help contributors using coding agents without encoding
one person's chat-delivery preferences into the project itself.

- [ ] Add a root `AGENTS.md` as a short universal entry point for AI coding tools.
- [ ] Make it point to the live `CONTRIBUTING.md`, architecture, owning plan and
  checks instead of duplicating current release facts.
- [ ] Record the dangerous invariants prominently: append-only reviews, explicit
  Room migrations, no destructive fallback, global target identity, contexts are
  observations rather than cards, and Catalogue provenance gates fail closed.
- [ ] Add a repository-safe `skills/ikna-development/` only if it remains useful
  beyond `AGENTS.md` and can be kept synchronized with repository docs.
- [ ] Keep personal workflow rules (for example a preferred artifact delivery
  format) out of the repository version of the skill.
- [ ] Document that agents without Skill support should follow `AGENTS.md` and the
  same repository-owned checks.

Acceptance: an unfamiliar AI coding tool can discover the correct project
contracts before editing without needing hidden chat history.

## Track J - visual character, after usability

Visual character comes after correctness and usability. It must never carry
critical state that is unavailable as text/semantics.

- [x] Use one shared README banner for the Russian and English README headers.
- [x] Establish Signal Frame as Ikna's interactive pointer/focus motif: one-pixel
  accent segments, softly rounded only in the signal frame, moving around the
  hovered object and staying static for keyboard focus/reduced motion.
- [x] Move Signal Frame motion onto one shared phase clock so rapid pointer travel
  does not repeatedly start/stop independent control animations.
- [x] Give nested interactive targets exclusive hover ownership: a child button
  suppresses the parent frame while the pointer is inside the child.
- [x] Scale segment rhythm for compact controls and keep the current desktop
  navigation destination moving at a quieter persistent intensity.
- [x] Tune Signal Frame speed, final segment rhythm and radius on a real Windows
  build before extending it to every remaining direct-click surface. Closed by
  owner decision (2026-09-25): the shipped motion is accepted as final.
- [x] Prototype the top "memory lattice" ambient animation inspired by the subtle
  appearing/disappearing block motif discussed during 0.11 work. Implemented as
  `IknaMemoryAmbientStrip` on the window title bar and the bottom bar, reusing
  the existing field's pitch, mark size and alpha ladder without new presets.
- [ ] Make the Ikna version its own restrained visual language rather than a copy
  of another application's navigation treatment.
- [ ] Keep movement slow, low-contrast and sparse; avoid shimmer, streak/progress
  meaning, XP language or attention-grabbing loops.
- [x] Implement it as one lightweight drawing surface rather than many constantly
  recomposing UI elements.
- [x] Respect the existing animation-off / reduced-motion behaviour and provide a
  fully static state.
- [ ] Check phone and desktop density separately; the motif may be shorter on
  mobile but should remain recognizably the same system.
- [ ] Build a second interface appearance as a first-class variant. Variant 1 is
  the shipped angular system - every corner a right angle, the current
  `IknaShapes`. Variant 2 is a rounded look: the first time the application, a
  couple of months into its life, tries corner rounding at all. Owner decision
  recorded 2026-09-26. Both variants must come from one component tree driven
  through the shape system rather than a forked copy of the UI, every hard
  right-angle assumption in shared composables needs an inventory before the
  rounded pass, and DESIGN.md gains the variant note when variant 2 lands.

Acceptance: the top chrome feels alive without competing with learning content or
making the interface less calm.

## Track K - large refactors and deferred debt

[`REFACTOR.md`](REFACTOR.md) describes structural splits that may improve
maintainability but have a much larger verification surface than ordinary cleanup.

- [ ] `REVIEW AFTER 0.11`: split `LearningRepository` only as a move-first,
  behaviour-unchanged refactor with one shared write lock and unchanged public
  facade.
- [ ] `REVIEW AFTER 0.11`: split the large Settings screen by existing sections
  without changing order, wording or keys in the move commit.
- [ ] Do not mix either split with policy, persistence or UX changes in the same
  change set.

These refactors may move earlier only if the current file structure becomes a
measured blocker to correctness or verification.

## Track L - fast desktop development loop

The purpose of this track is to remove release-build latency from ordinary UI
iteration without changing production behaviour or requiring a second checkout.
Hot Reload is a development accelerator, not release evidence.

- [x] Pin Compose Hot Reload `1.1.1`, the stable line that still supports the
  current Compose Multiplatform `1.8.2` baseline.
- [x] Apply Hot Reload only to the existing JVM desktop application; do not add a
  fake preview app or duplicate shared UI.
- [x] Add `dev-hot-reload.cmd` as the one-step Windows entry point and keep the
  repository's Gradle `8.10.2` pin by bootstrapping that distribution outside the
  source tree when necessary.
- [x] Allow Hot Reload to provision its JetBrains Runtime while leaving normal
  application bytecode/toolchain targeting at JVM 17.
- [x] Run hot development against an isolated desktop home and the existing
  Developer Mode profile by default, with an explicit `-UseRealData` escape hatch.
- [x] Keep Gradle/tool downloads, development profile data and hot-run logs under
  `%LOCALAPPDATA%`, so replacing the visible contents of the Git checkout with a
  full ZIP does not throw away the expensive caches.
- [x] Make the launcher tolerate a temporarily incomplete source tree during the
  existing full-ZIP replacement workflow and keep the terminal/log path visible
  if the hot-run process exits.
- [x] Document what is suitable for Hot Reload and what still requires restart,
  generated-code checks or a real target build.
- [ ] Verify on a real Windows machine that edits in `shared/` (not only
  `desktop/`) reload into the already-running Ikna window.
- [ ] Measure first launch and repeated `shared` UI reload latency on the normal
  development machine; the repeated path should be seconds rather than minutes.
- [ ] Exercise a deliberate Kotlin compile error, runtime Compose error and whole
  source-tree ZIP replacement; refine the launcher if any case loses the useful
  error or requires avoidable manual recovery.
- [ ] After the basic loop is proven, decide whether a richer inspector needs
  dedicated Copy error/Open log/Restart controls beyond Compose Hot Reload's
  built-in dev feedback and the persistent log files.
- [ ] `REVIEW AFTER 0.11`: evaluate Compose `1.10+` and Hot Reload `1.2+` for the
  MCP/semantic-tree/screenshot tooling instead of coupling that toolchain upgrade
  to the first fast-loop implementation.
- [ ] `REVIEW AFTER 0.11`: extract the proven launcher/supervisor pattern into a
  reusable multi-project tool only after Ikna demonstrates which parts are truly
  generic.

Acceptance: from the ordinary Git/GitHub Desktop checkout, a developer can start
one hot desktop session, replace/edit source without committing, and see a shared
Compose UI change in the running window without producing a release package; a
failed iteration leaves actionable build/runtime logs and never touches normal
learner data unless `-UseRealData` was chosen deliberately.

## Modernization closeout checklist

Before calling this modernization plan complete:

- [ ] Android build and relevant JVM/unit tests are green on the final tree.
- [ ] Desktop build and tests are green on the final tree.
- [ ] Text/localization/design/palette checks are green.
- [ ] Developer Sandbox isolation and forced-access regressions are covered.
- [ ] Browse is verified as a passive vertical reading feed on both platforms.
- [ ] Multi-monitor desktop restore has real-platform evidence.
- [ ] Dead-code/dependency/resource removals each have a documented proof of
  ownership or non-use.
- [ ] Documentation no longer copies volatile current-state facts without a clear
  owner.
- [ ] AI contributor guidance is discoverable from the repository root.
- [ ] Reduced-motion behaviour covers any added ambient animation.
- [ ] The full project archive/package preserves the repository source manifest,
  including intentionally tracked binary/signing assets.
- [ ] `PLAN-0.11.md` still owns the real release blockers and numbered 0.11 parts;
  this file has not become a competing release plan.
