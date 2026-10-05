#!/usr/bin/env python3
"""Report ikna source metadata and its repository-owned active plan, read-only."""
from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

REQUIRED = (
    "CONTRIBUTING.md", "docs/ARCHITECTURE.md", "app/build.gradle.kts",
    "desktop/build.gradle.kts", "shared/build.gradle.kts",
)


def active_plan(repo: Path, guide: str) -> Path:
    markers = re.findall(r"<!--\s*ikna-active-plan:\s*([^\s]+)\s*-->", guide)
    if not markers:
        # Support older snapshots that link a single working plan without a marker.
        markers = sorted(set(re.findall(r"\((docs/PLAN-[0-9.]+\.md)\)", guide)))
    if len(markers) != 1:
        raise ValueError("CONTRIBUTING must identify exactly one active working plan")
    rel = Path(markers[0])
    path = (repo / rel).resolve()
    if rel.is_absolute() or ".." in rel.parts or not path.is_relative_to(repo):
        raise ValueError("active plan must be a relative path inside the repository")
    if not path.is_file():
        raise ValueError(f"active plan is missing: {rel.as_posix()}")
    return path


def git(repo: Path, *args: str) -> str | None:
    try:
        result = subprocess.run(
            ["git", "-C", str(repo), *args], capture_output=True, text=True,
            check=False, timeout=10,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo", type=Path)
    args = parser.parse_args()
    repo = args.repo.expanduser().resolve()
    missing = [name for name in REQUIRED if not (repo / name).is_file()]
    if missing:
        print("ERROR: missing required ikna source files: " + ", ".join(missing))
        return 1
    guide = (repo / "CONTRIBUTING.md").read_text(encoding="utf-8")
    try:
        plan = active_plan(repo, guide)
    except ValueError as error:
        print(f"ERROR: {error}")
        return 1

    print(f"repo: {repo}")
    # Avoid mistaking a surrounding workspace's Git metadata for this project.
    if (repo / ".git").exists():
        branch = git(repo, "branch", "--show-current")
        status = git(repo, "status", "--short")
        print(f"git branch: {branch or '(unavailable or detached)'}")
        print("git status: " + ("unavailable" if status is None else
              "clean" if not status else f"{len(status.splitlines())} changed/untracked paths"))
    else:
        print("git metadata: absent (not required for source inspection)")

    probes = (
        ("app/build.gradle.kts", "android versionName",
         r'\b(?:val\s+appVersionName|versionName)\s*=\s*"([^"]+)"'),
        ("app/build.gradle.kts", "android versionCode",
         r"\b(?:val\s+appVersionCode|versionCode)\s*=\s*([0-9]+)"),
        ("desktop/build.gradle.kts", "desktop packageVersion",
         r'\bpackageVersion\s*=\s*"([^"]+)"'),
        ("shared/src/jvmShared/kotlin/dev/ikna/data/db/IknaDatabase.kt",
         "Room schema version", r"\bversion\s*=\s*([0-9]+)"),
    )
    for name, label, pattern in probes:
        path = repo / name
        match = re.search(pattern, path.read_text(encoding="utf-8")) if path.is_file() else None
        print(f"{label}: {match.group(1) if match else 'unavailable'}")
    text = plan.read_text(encoding="utf-8")
    heading = re.search(r"^#\s+(.+)$", text, re.MULTILINE)
    blockers = bool(re.search(r"^##\s+(?:🚧\s+)?Release blockers\s*$", text, re.MULTILINE))
    print(f"active plan: {plan.relative_to(repo).as_posix()}")
    print(f"planning cycle: {heading.group(1) if heading else '(untitled)'}")
    print(f"release-blockers section: {'yes' if blockers else 'no'}")
    print("read next: CONTRIBUTING, ARCHITECTURE, active plan and owning contracts")
    print("preflight is read-only; it does not prove tests/builds pass")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
