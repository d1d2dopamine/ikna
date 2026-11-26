---
name: ikna-owner-workflow
description: Apply the ikna project owner's personal development, verification, and Git/CI delivery workflow when the owner explicitly selects it. Use together with ikna-development for Russian communication, fast no-Gradle local checks, GitHub CI inspection via gh CLI, zero infinite polling timers, and optional full-ZIP delivery only when explicitly requested. Do not apply these personal rules to ordinary contributors automatically.
---

# Ikna Owner Workflow

Apply this personal workflow only when selected by the project owner. Read
`../ikna-development/SKILL.md`, `../ikna-development/references/agent-pitfalls.md`,
and `../ikna-development/references/check-matrix.md` first; use the common
data-safety, scope and verification rules. Keep both skills alongside each other
in the repository. Do not duplicate learning or corpus contracts here.

## Work with the owner

- Communicate in Russian by default (concise, direct, no fluff), while keeping
  code, commits, release titles/notes, and technical docs in English unless the
  owner asks otherwise.
- Work from the current verified `origin/main` checkout (run `git fetch` /
  check ahead-behind before editing when multiple chats may push to the repo).
- Follow the active working plan linked from `CONTRIBUTING.md` and the owner's
  latest instructions. Newer owner decisions override stale plan checkboxes.
- Complete authorized reversible work cleanly without asking for repeated
  permission on every minor step. Respect explicit authorization for tags,
  releases, public Catalogue publication, and destructive actions.
- Respect the owner's product principles strictly: calm, clean UI, zero empty
  gamification/streaks, zero duplicate lists, zero silent failures.
- Describe results, checks and limitations plainly. Never present static checks
  as full compilation or device-test evidence.

## Local verification & CI rules (owner machine)

- **Prefer fast local checks over heavy local Gradle:** Unless the owner
  explicitly asks for a local Gradle build/test run, do NOT run full Gradle
  compilation, Android APK builds, or hot-reload locally. Run the fast local
  pre-push gate from `check-matrix.md` (`check_localization.py`,
  `check_text.py`, `check_design_parity.py`, `check_grading.py`,
  `tools/grading/check_full.py`, `check_optimizer.py`, `check_classic_cards.py`,
  `check_android_ci.py`, `check_palettes.py`, `SettingsSourceContracts.java`,
  `git diff --check`) plus `git grep` on every moved/renamed symbol.
- **Let GitHub Actions CI be the real compile & test gate:** Group related
  changes into clean, logical commits and push to `origin/main` when authorized.
- **Inspect CI via `gh` CLI — never a browser:** Use `gh run list` and
  `gh run view <id> --log-failed` to diagnose CI failures directly.
- **Never set infinite timers or polling loops:** Do not schedule recurring
  cron/timer loops or shell sleeps waiting for CI to finish. Push, report what
  was verified locally, and stop.

## Delivery mode

- **Default (Git checkout):** Commit cleanly and push to `origin/main` (when
  authorized for the task), summarizing changed files with clickable links and
  the exact checks run.
- **Optional full-ZIP delivery (only when explicitly requested or in an offline
  archive-only session without Git):**
  - Run `scripts/package_repo.py <repo-root> <output.zip>` (or with
    `--source-archive <input.zip>` and `--add <relative-path>` for every added
    file when `.git` is absent).
  - Preserve every tracked file including `ikna.keystore` and schemas; exclude
    `.git` and machine-local build clutter; keep the archive outside the source
    tree.
  - Use `--allow-delete <path>` only for explicitly requested tracked deletions.

For a read-only audit or discussion, do not modify repository files and do not
create commits or archives.

## Resource

- `scripts/package_repo.py`: package tracked files and new non-ignored files for
  explicit ZIP-delivery sessions; reject unexplained deletions and verify the
  finished archive.
