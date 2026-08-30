#!/usr/bin/env python3
"""Attach a built catalogue to one GitHub release, slowly enough to be allowed to.

Two hundred and seventeen files is not much data -- the whole catalogue is
smaller than one podcast episode -- but it is a great many *requests*, and
GitHub counts requests rather than bytes. Above roughly eighty
content-generating requests a minute, or five hundred an hour, it stops
answering and says so:

    You have exceeded a secondary rate limit.

The usual release action uploads everything as fast as it can and meets that
wall about two thirds of the way through. What it leaves behind is worse than
nothing: a release holding most of a catalogue and an index promising all of
it, which is precisely the shape of failure a phone cannot recover from.

So this script uploads the same files at a deliberate pace, waits when it is
told to wait, and -- the part that matters on the second attempt -- skips
whatever is already up there. A rerun after a rate limit costs a handful of
requests instead of another four hundred, and picks up at the deck it stopped
on.

Three orderings are deliberate.

  * index.json goes last, always. The phone reads the index to learn what
    exists; an index that arrives before its decks is an index that lies.
    Uploaded last, a half-finished publish is merely incomplete, never wrong.
  * a deck is replaced only when its size differs from the asset already
    attached. Deleting an asset costs a request as surely as uploading one,
    and the budget is spent either way.
  * every rate-limit answer permanently slows the remainder of the run. The
    limit is not published exactly and is allowed to change without notice, so
    the only honest response to meeting it is to stop arguing.

Needs the gh CLI, which every GitHub runner already has, and GH_TOKEN in the
environment with contents: write.

Usage:

    python3 tools/catalog/publish.py --dir catalog --tag catalog \\
        --title "deck catalogue" --notes catalog/TIERS.md --repo owner/name
"""

import argparse
import json
import os
import subprocess
import sys
import time

# The index is uploaded after every deck it describes.
INDEX = "index.json"

# GitHub does not name the limit it enforced, so the wording is matched loosely.
# A false positive here costs a minute of waiting; a false negative costs the
# rest of the run.
RATE_WORDS = (
    "secondary rate limit",
    "rate limit",
    "abuse detection",
    "submitted too quickly",
    "retry-after",
    "403",
    "429",
)

# Published figures, at the time of writing: 80 content-generating requests a
# minute and 500 an hour. Neither is a promise. The defaults below sit well
# under both, because a publish that takes ten minutes and works is better than
# one that takes three and does not.
DEFAULT_PER_MINUTE = 30
HOUR_BUDGET = 460


def shell(args, payload=None):
    """Run a command, returning (status, combined output). Never raises."""
    try:
        done = subprocess.run(
            args,
            input=payload,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except OSError as error:
        return 127, str(error)
    return done.returncode, done.stdout or ""


class Pacer:
    """Keeps a minimum gap between requests, and widens it when told off."""

    def __init__(self, per_minute):
        self.per_minute = max(1, int(per_minute))
        self.last = 0.0
        self.spent = 0

    @property
    def gap(self):
        return 60.0 / self.per_minute

    def wait(self):
        rest = self.last + self.gap - time.monotonic()
        if rest > 0:
            time.sleep(rest)
        self.last = time.monotonic()
        self.spent += 1

    def slow_down(self):
        """Halve the rate, to a floor of six a minute."""
        before = self.per_minute
        self.per_minute = max(6, self.per_minute // 2)
        if self.per_minute != before:
            print(
                "    slowing from %d to %d requests a minute"
                % (before, self.per_minute),
                flush=True,
            )


def looks_rate_limited(text):
    low = (text or "").lower()
    return any(word in low for word in RATE_WORDS)


def backoff_seconds(text, attempt):
    """How long to wait before trying the same file again."""
    if looks_rate_limited(text):
        # GitHub asks for "a few minutes". Give it a few minutes.
        return min(600, 60 * attempt)
    return min(60, 5 * attempt)


def read_release(repo, tag):
    """The release for a tag, or None when there is not one yet."""
    status, out = shell(
        ["gh", "api", "repos/%s/releases/tags/%s" % (repo, tag)]
    )
    if status != 0:
        return None
    try:
        return json.loads(out)
    except ValueError:
        return None


def create_release(repo, tag, title, notes, dry_run):
    body = {
        "tag_name": tag,
        "name": title,
        "body": notes,
        "draft": False,
        "prerelease": False,
        # A catalogue holding the "latest release" title would offer the update
        # check a download with no APK in it.
        "make_latest": "false",
    }
    if dry_run:
        print("would create release %s" % tag, flush=True)
        return True
    status, out = shell(
        ["gh", "api", "--method", "POST", "repos/%s/releases" % repo, "--input", "-"],
        payload=json.dumps(body),
    )
    if status != 0:
        print("could not create release %s: %s" % (tag, out.strip()), flush=True)
        return False
    print("created release %s" % tag, flush=True)
    return True


def edit_release(repo, release, title, notes, dry_run):
    body = {
        "name": title,
        "body": notes,
        "draft": False,
        "prerelease": False,
        "make_latest": "false",
    }
    if dry_run:
        print("would refresh the notes on release %s" % release.get("tag_name"), flush=True)
        return True
    status, out = shell(
        [
            "gh",
            "api",
            "--method",
            "PATCH",
            "repos/%s/releases/%s" % (repo, release["id"]),
            "--input",
            "-",
        ],
        payload=json.dumps(body),
    )
    if status != 0:
        print("could not update the release notes: %s" % out.strip(), flush=True)
        return False
    return True


def assets_of(release):
    """Name to size, for what is already attached."""
    sizes = {}
    for asset in (release or {}).get("assets") or []:
        name = asset.get("name")
        if name:
            sizes[name] = asset.get("size")
    return sizes


def local_files(folder):
    """Every deck, in name order, with the index last."""
    names = sorted(
        name
        for name in os.listdir(folder)
        if name.endswith(".jsonl") and os.path.isfile(os.path.join(folder, name))
    )
    if os.path.isfile(os.path.join(folder, INDEX)):
        names.append(INDEX)
    return names


def upload(repo, tag, path, clobber, pacer, attempts, dry_run):
    """One file, with patience. True when it is up there."""
    command = ["gh", "release", "upload", tag, path, "--repo", repo]
    if clobber:
        command.append("--clobber")
    if dry_run:
        print("    would %s" % ("replace" if clobber else "upload"), flush=True)
        return True
    for attempt in range(1, attempts + 1):
        pacer.wait()
        status, out = shell(command)
        if status == 0:
            return True
        limited = looks_rate_limited(out)
        if limited:
            pacer.slow_down()
        if attempt == attempts:
            print("    gave up: %s" % out.strip()[-400:], flush=True)
            return False
        rest = backoff_seconds(out, attempt)
        print(
            "    attempt %d of %d failed (%s), waiting %ds"
            % (attempt, attempts, "rate limit" if limited else "error", rest),
            flush=True,
        )
        time.sleep(rest)
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dir", required=True, help="folder holding the built catalogue")
    parser.add_argument("--tag", default="catalog", help="release tag to attach to")
    parser.add_argument("--title", default="deck catalogue", help="release title")
    parser.add_argument("--notes", default=None, help="file to use as the release body")
    parser.add_argument(
        "--repo",
        default=os.environ.get("GITHUB_REPOSITORY", ""),
        help="owner/name; defaults to GITHUB_REPOSITORY",
    )
    parser.add_argument(
        "--per-minute",
        type=int,
        default=DEFAULT_PER_MINUTE,
        help="requests a minute at most (default %d)" % DEFAULT_PER_MINUTE,
    )
    parser.add_argument(
        "--attempts", type=int, default=6, help="tries per file before giving up"
    )
    parser.add_argument(
        "--replace-all",
        action="store_true",
        help="re-upload every file, even one already attached at the same size",
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="say what would happen, change nothing"
    )
    args = parser.parse_args()

    if not args.repo:
        print("no repository: pass --repo owner/name", flush=True)
        return 2
    if not os.path.isdir(args.dir):
        print("no such folder: %s" % args.dir, flush=True)
        return 2

    names = local_files(args.dir)
    if not names:
        print("nothing to publish in %s" % args.dir, flush=True)
        return 2

    notes = ""
    if args.notes and os.path.isfile(args.notes):
        with open(args.notes, encoding="utf-8") as handle:
            notes = handle.read()

    release = read_release(args.repo, args.tag)
    if release is None:
        if not create_release(args.repo, args.tag, args.title, notes, args.dry_run):
            return 1
        release = read_release(args.repo, args.tag) or {}
    elif notes:
        edit_release(args.repo, release, args.title, notes, args.dry_run)

    attached = assets_of(release)
    pacer = Pacer(args.per_minute)

    skipped = []
    failed = []
    done = 0

    print(
        "%d files to publish to %s@%s, %d already attached"
        % (len(names), args.repo, args.tag, len(attached)),
        flush=True,
    )

    for number, name in enumerate(names, start=1):
        if name == INDEX and failed:
            # The index names every deck. Attaching it while a deck is missing
            # would hand the phone a list it cannot honour, so it stays behind.
            print(
                "[%d/%d] holding back %s: %d deck(s) did not go up"
                % (number, len(names), INDEX, len(failed)),
                flush=True,
            )
            break
        path = os.path.join(args.dir, name)
        size = os.path.getsize(path)
        there = attached.get(name)
        if not args.replace_all and there == size:
            skipped.append(name)
            continue
        print(
            "[%d/%d] %s %s (%d bytes)"
            % (number, len(names), "replacing" if there is not None else "uploading", name, size),
            flush=True,
        )
        clobber = there is not None
        if clobber:
            # A replacement is a delete and an upload, and GitHub counts both.
            pacer.spent += 1
        if upload(args.repo, args.tag, path, clobber, pacer, args.attempts, args.dry_run):
            done += 1
        else:
            failed.append(name)
            if name == INDEX:
                break
        if pacer.spent > HOUR_BUDGET:
            print(
                "    %d requests spent; the hourly allowance is near, resting a minute"
                % pacer.spent,
                flush=True,
            )
            time.sleep(60)
            pacer.spent = 0

    print("", flush=True)
    print(
        "%d uploaded, %d already current, %d failed" % (done, len(skipped), len(failed)),
        flush=True,
    )
    if failed:
        print("failed: %s" % ", ".join(failed[:20]), flush=True)
        print(
            "run this workflow again: what is already attached will be skipped, "
            "and it will carry on from here",
            flush=True,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
