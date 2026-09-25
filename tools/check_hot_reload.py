#!/usr/bin/env python3
"""Source-level contract check for Ikna's desktop Hot Reload development loop."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    root_build = text("build.gradle.kts")
    desktop_build = text("desktop/build.gradle.kts")
    props = text("gradle.properties")
    main_kt = text("desktop/src/main/kotlin/dev/ikna/desktop/Main.kt")
    launcher = text("tools/dev-hot-reload.ps1")
    cmd = text("dev-hot-reload.cmd")
    docs = text("docs/HOT-RELOAD.md")
    plan = text("docs/modern_PLAN-0.11.md")

    require(
        'id("org.jetbrains.compose.hot-reload") version "1.1.1" apply false' in root_build,
        "root build must pin Compose Hot Reload 1.1.1",
    )
    require(
        'id("org.jetbrains.compose.hot-reload")' in desktop_build,
        "desktop module must apply Compose Hot Reload",
    )
    require(
        "compose.reload.jbr.autoProvisioningEnabled=true" in props,
        "hot reload JBR auto-provisioning must stay enabled",
    )
    require(
        'System.getenv("IKNA_HOME_OVERRIDE")' in main_kt,
        "desktop must support an explicit isolated dev home",
    )
    require(
        ':desktop:hotRun --auto' in launcher,
        "launcher must use desktop hotRun in auto mode",
    )
    require(
        '$GradleVersion = "8.10.2"' in launcher and 'services.gradle.org/distributions/gradle-$GradleVersion-bin.zip' in launcher,
        "launcher must bootstrap the repository-pinned Gradle 8.10.2",
    )
    require(
        '"ikna-data-profile"' in launcher and '"DEVELOPER"' in launcher,
        "default hot session must select isolated Developer Mode",
    )
    require(
        "Tee-Object -FilePath" in launcher,
        "launcher must persist Gradle/compiler output",
    )
    require(
        "Waiting for the new ZIP contents" in launcher,
        "launcher must tolerate temporary source-tree replacement",
    )
    require(
        "tools\\dev-hot-reload.ps1" in cmd,
        "root cmd launcher must delegate to the PowerShell supervisor",
    )
    require(
        "-NoExit" in cmd,
        "double-click launcher must keep PowerShell open after script-level failures",
    )
    require(
        "No second checkout" in docs,
        "docs must preserve the single-repository workflow",
    )
    require(
        "## Track L - fast desktop development loop" in plan,
        "modernization plan must own the hot-reload track",
    )

    print("Hot Reload source contract: PASS")
    print("  plugin: 1.1.1")
    print("  task: :desktop:hotRun --auto")
    print("  Gradle bootstrap: 8.10.2")
    print("  data: isolated Developer Mode by default")
    print("  source replacement/logging: wired")
    print("  terminal persistence: PowerShell -NoExit")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as error:
        print(f"Hot Reload source contract: FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
