---
name: ikna-owner-workflow
description: Apply the ikna project owner's personal development and delivery workflow when the owner explicitly selects it. Use together with ikna-development to work from the current project snapshot, follow the active plan, and deliver all requested modifications as one verified complete repository ZIP. Do not apply these personal delivery rules to ordinary contributors automatically.
---

# Ikna Owner Workflow

Apply this personal workflow only when selected by the project owner. Read
`../ikna-development/SKILL.md` and its relevant resources first; use its common
data-safety, scope and verification rules. Keep both skills alongside each other
in the repository. Do not duplicate learning or corpus contracts here.

## Work with the owner

- Use Russian by default unless the owner asks for another language.
- Work from the current verified snapshot, including all previous accepted
  changes. Do not start a later task from an older output archive.
- Follow the active working plan linked from CONTRIBUTING. Re-read the plan and
  owner decisions before choosing the next task. Keep the planning cycle distinct
  from the version currently stored in build files.
- Use the active plan's allocation between Catalogue v2 and application work
  across planned batches; do not force every individual patch to touch both.
- Complete authorized reversible work without repeated permission requests.
  Respect action-specific authorization for publishing and destructive actions.
- Use the supplied files or an authorized connected GitHub source. Do not install
  Git, create a new local repository or set up a checkout merely to prepare a ZIP.
  Set up additional tooling only when the owner requests or authorizes it.
- Describe results, checks and limitations plainly. Do not present fixture-only
  checks as release evidence or imply an unavailable platform was tested.

## Deliver one complete project ZIP after modifications

After modifying the project, return one full ZIP containing the complete current
source tree with all requested changes already applied. Include both repository
skills. Treat the archive as the owner's default code deliverable, not as a
contributor-wide requirement.

- Combine changes from the same task in one verified tree and one archive.
  Create separate variants only if the owner asks for them.
- Do not deliver `.patch` or `.diff` artifacts unless explicitly requested.
- Preserve every tracked source file unless its deletion is an explicit part of
  the task. Preserve `ikna.keystore` and other tracked binary/signing/schema
  assets according to the repository contracts.
- With an existing Git checkout, exclude `.git` and ignored/untracked
  machine-local clutter. Do not apply an extra category denylist to tracked files.
- With a supplied ZIP, preserve its complete source manifest. Explicitly list
  every added source file; do not infer source additions from a recursive scan
  that can accidentally include build outputs or local caches.
- Review the diff before packaging. Keep the output archive outside the source
  tree so it cannot include or overwrite itself.
- For an existing checkout, run
  `scripts/package_repo.py <repo-root> <output.zip>`. For an archive workflow,
  run the same script with `--source-archive <input.zip>` and repeat
  `--add <relative-path>` for every added source file. Require exact source/ZIP
  manifest agreement, byte verification and ZIP integrity.
- Use `--allow-delete <path>` only for a tracked deletion explicitly included in
  the requested change.
- When `.git` is missing, use the supplied project ZIP as the authoritative
  source manifest. Verify the source baseline before editing and review changed
  bytes/paths against it before packaging. Do not create Git metadata for this.
- If a full archive cannot be produced, report the limitation. Do not silently
  substitute a patch.

For a read-only review, return the findings; no new project ZIP is required.
Return the archive through the delivery mechanism available in the current tool.
Keeping these skills in the repository does not install them into any service or
personal skill directory.

## Resource

- `scripts/package_repo.py`: package tracked files and new non-ignored files;
  reject unexplained deletions and verify the finished archive.
