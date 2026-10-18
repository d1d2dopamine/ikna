#!/usr/bin/env python3
"""Compile actual narrow production/test sources with Kotlin 2.2.20 and JDK 17.

This does not configure/build the application. Windows tests skip on other OSes.
Downloads only hash-pinned Maven Central dependencies into the chosen work directory.
No corpus, account, user-state or system-permission changes are performed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import urllib.request

HERE = Path(__file__).resolve().parent
SOURCES = [
    "shared/src/jvmShared/kotlin/dev/ikna/sync/PrivateSyncFiles.kt",
    "shared/src/jvmShared/kotlin/dev/ikna/sync/LanSyncSecurity.kt",
    "shared/src/jvmSharedTest/kotlin/dev/ikna/sync/PrivateSyncFilesTest.kt",
    "shared/src/desktopTest/kotlin/dev/ikna/sync/PrivateSyncFilesWindowsTest.kt",
]
TESTS = ["dev.ikna.sync.PrivateSyncFilesTest", "dev.ikna.sync.PrivateSyncFilesWindowsTest"]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--work-dir", required=True, type=Path)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    repo, work = args.repo.resolve(), args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    deps, classes = work / "deps", work / "classes"
    deps.mkdir(exist_ok=True)
    classes.mkdir(exist_ok=True)
    suffix = ".exe" if os.name == "nt" else ""
    def executable(name):
        return str(Path(args.java_home) / "bin" / (name + suffix)) if args.java_home else name + suffix
    def run(label, command):
        command = list(map(str, command))
        (work / f"{label}-command.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (work / f"{label}.log").write_text(result.stdout, encoding="utf-8")
        if result.returncode:
            print(result.stdout)
            raise SystemExit(result.returncode)
        return result.stdout
    version = run("java-version", [executable("java"), "-version"])
    if 'version "17.' not in version:
        raise SystemExit("This evidence runner requires JDK 17; no alternate-version evidence is implied.")
    dependencies = json.loads((HERE / "dependencies.json").read_text(encoding="utf-8"))
    for item in dependencies:
        path = deps / item["name"]
        if not path.exists():
            with urllib.request.urlopen(item["url"], timeout=120) as response:
                path.write_bytes(response.read())
        if digest(path) != item["sha256"]:
            raise SystemExit(f"Dependency hash mismatch: {item['name']}")
    compiler = os.pathsep.join(str(deps / x["name"]) for x in dependencies if x["role"] in ("compiler", "both"))
    runtime = os.pathsep.join(str(deps / x["name"]) for x in dependencies if x["role"] in ("runtime", "both"))
    run("compile", [executable("java"), "-Xmx768m", "-cp", compiler,
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
        "-classpath", runtime, "-d", classes, *(repo / source for source in SOURCES)])
    runtime = str(classes) + os.pathsep + runtime
    run("compile-runner", [executable("javac"), "-cp", runtime, "-d", classes, HERE / "TestEvidence.java"])
    counts = json.loads(run("tests", [executable("java"), "-cp", runtime, "TestEvidence", *TESTS]))
    result = {
        "scope": "Isolated actual PrivateSyncFiles/LanSyncSecurity sources; not a full Gradle/application build",
        "platform": platform.platform(), "java": version, "kotlin_compiler": "2.2.20", "counts": counts,
        "source_sha256": {source: digest(repo / source) for source in SOURCES},
        "runner_sha256": {name: digest(HERE / name) for name in ("run_isolated.py", "dependencies.json", "TestEvidence.java")},
        "evidence_sha256": {path.name: digest(path) for path in work.glob("*-command.json")},
    }
    (work / "RESULTS.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))

if __name__ == "__main__":
    main()
