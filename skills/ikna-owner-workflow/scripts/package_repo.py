#!/usr/bin/env python3
"""Package ikna source from existing Git metadata or an explicit source ZIP manifest."""
from __future__ import annotations

import argparse
import hashlib
import subprocess
import tempfile
import zipfile
from pathlib import Path, PurePosixPath


def source_path(value: str) -> str:
    path = PurePosixPath(value)
    if (not value or path.is_absolute() or ".." in path.parts or
            ".git" in path.parts or "\\" in value or ":" in value or
            path.as_posix() != value or value == "."):
        raise ValueError(f"unsafe or non-canonical source path: {value}")
    return value


def git_paths(repo: Path, *args: str) -> set[str]:
    result = subprocess.run(
        ["git", "-C", str(repo), "ls-files", "-z", *args],
        capture_output=True, check=False,
    )
    if result.returncode:
        raise ValueError("cannot read existing Git source manifest")
    return {source_path(item.decode("utf-8")) for item in result.stdout.split(b"\0") if item}


def manifest(repo: Path, archive: Path | None, additions: list[str]) -> tuple[set[str], set[str]]:
    if archive is not None:
        with zipfile.ZipFile(archive) as source:
            if source.testzip() is not None:
                raise ValueError("input source ZIP failed integrity verification")
            names = [source_path(info.filename) for info in source.infolist() if not info.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError("input source ZIP has duplicate file paths")
        baseline = set(names)
        added = {source_path(name) for name in additions}
        if added & baseline:
            raise ValueError("--add must name a new source path, not an existing baseline file")
        if len(added) != len(additions):
            raise ValueError("duplicate --add path")
        # Never scan unknown build/caches into an archive-based source package.
        return baseline, added

    if additions:
        raise ValueError("--add is only needed with --source-archive")
    if not (repo / ".git").exists():
        raise ValueError("no Git metadata; supply --source-archive and explicit --add paths")
    top = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "--show-toplevel"],
        capture_output=True, text=True, check=False,
    )
    if top.returncode or Path(top.stdout.strip()).resolve() != repo:
        raise ValueError("repo must be the root of the existing Git worktree")
    return git_paths(repo, "--cached"), git_paths(repo, "--others", "--exclude-standard")


def package(args: argparse.Namespace) -> tuple[int, int]:
    repo = args.repo.expanduser().resolve()
    output = args.output.expanduser().resolve()
    archive = args.source_archive.expanduser().resolve() if args.source_archive else None
    if not repo.is_dir():
        raise ValueError("source repository directory does not exist")
    if output.is_relative_to(repo) or output == archive:
        raise ValueError("output must be outside the source tree and must not overwrite the input ZIP")
    baseline, added = manifest(repo, archive, args.add)
    missing = {name for name in baseline if not (repo / name).exists()}
    allowed = {source_path(name) for name in args.allow_delete}
    if missing - allowed:
        raise ValueError("unexplained missing source files: " + ", ".join(sorted(missing - allowed)))
    if allowed - missing:
        raise ValueError("--allow-delete names a path that is not a missing baseline file")
    members = sorted((baseline - missing) | added)
    if not members:
        raise ValueError("source manifest is empty")

    hashes = {}
    for name in members:
        path = repo / name
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(repo):
            raise ValueError("source member must be a regular file inside the repository: " + name)
        hashes[name] = hashlib.sha256(path.read_bytes()).digest()

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, prefix=output.name + ".", suffix=".tmp", delete=False) as f:
        temp = Path(f.name)
    try:
        with zipfile.ZipFile(temp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zipped:
            for name in members:
                zipped.write(repo / name, arcname=name)
        with zipfile.ZipFile(temp) as zipped:
            if sorted(zipped.namelist()) != members or zipped.testzip() is not None:
                raise ValueError("ZIP source manifest or integrity verification failed")
            for name in members:
                digest = hashlib.sha256(zipped.read(name)).digest()
                current = hashlib.sha256((repo / name).read_bytes()).digest()
                if digest != hashes[name] or current != hashes[name]:
                    raise ValueError("source/ZIP bytes changed during packaging: " + name)
        temp.replace(output)
    finally:
        temp.unlink(missing_ok=True)
    return len(baseline - missing), len(added)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-archive", type=Path, help="authoritative input source ZIP, with paths relative to repo")
    parser.add_argument("--add", action="append", default=[], metavar="PATH", help="explicit new source file in archive mode")
    parser.add_argument("--allow-delete", action="append", default=[], metavar="PATH", help="explicitly requested deletion of a baseline file")
    args = parser.parse_args()
    try:
        original, added = package(args)
    except (ValueError, OSError, zipfile.BadZipFile, RuntimeError) as error:
        print(f"ERROR: {error}")
        return 1
    print(f"ZIP: {args.output.expanduser().resolve()}")
    print(f"files: {original + added} ({original} source-manifest, {added} new)")
    print("manifest, SHA-256 bytes and ZIP integrity: exact")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
