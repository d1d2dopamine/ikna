# Versions: proof and press

A version of ikna is a number **and a word**: `0.1.0 proof`. The number counts
releases inside an epoch; the word says **which epoch** the build belongs to, and
therefore what kind of change to expect from the next one.

The words come from printing. A *proof* is the copy you read back and correct
before the press runs; the *press* is the run itself.

| Epoch | Meaning | What lands in it |
| --- | --- | --- |
| **proof** | The proof stage. Everything is set. What is left is reading it back and correcting what is wrong. | Testing, bug fixes, small corrections, wording, polish. No new pillars. |
| **press** | The press run. Opens only when `proof` has nothing left to correct. | The next generation of the app — whatever is big enough to deserve a new word. |

Epoch words are lower case, like the app's own name: `0.1.0 proof`, never
`0.1.0 PROOF`. A version is a label on a build, not an announcement.

The current epoch is **press**, opened by `0.1.0 press`. An epoch is a road in one
direction: once the press has run, the word `proof` is not used again, because it
names an era that ended and not the size of a particular release.

## Inside an epoch

The word says which era; the number says what the release is. The patch number
carries corrections — bugs, wording, things taken out of the way — and the minor
number carries something that was not there before. A release inside `press` that
only fixes what is broken is `0.1.1 press`; it is not a return to `proof`, and it
does not promise less than the epoch does.

## Why the numbering restarted at 0.1.0

The pre-epoch `0.x` line (up to `0.6.1`) counted the app being assembled. The
`proof` epoch counts a finished app being hardened, so carrying `0.6.1` forward
would have implied the two scales are comparable. They are not.

One thing does **not** restart: `appVersionCode`. Android refuses to install an
APK whose code is lower than the installed one, and the only irreplaceable thing
in this app is the review log inside that install — so the internal counter keeps
climbing straight across the reset. The version **you read** restarts with each
epoch; the version **Android compares** never does.

Each epoch therefore owns a block of codes above everything shipped before it:
`proof` starts at `100000000`, `press` at `200000000`, and whatever follows will
start higher again. Inside a block, the number is built from the version name:

```
val appVersionName = "0.1.1 press"
val appVersionCode = 200010100    // epoch offset + major * 100000 + minor * 10000 + patch * 100
```

This is also why a build cannot go back to an earlier epoch: `0.6.0 proof` would
number `100060000`, below the `press` build already installed, and Android would
refuse the update — taking the review log with it if anyone forced the issue by
reinstalling.

There is exactly one place where a version number exists: `app/build.gradle.kts`.
Not the tag, because F-Droid builds this repository on a machine that knows nothing
about either tags or CI.

## Tags

Git tags carry the same string with the space turned into a dash, because a tag
cannot contain one: `v0.1.0-proof`. The `release` workflow refuses to publish if
the tag and the build file disagree, so the About line in the app and the file on
the release page cannot drift apart.

## 0.9.0 press

- `versionName`: `0.9.0 press`
- `versionCode`: `200090000`
- release tag: `v0.9.0-press`
- database schema: 4 (the governor log gains derived columns; the review log is
  not touched)
- scheduler: FSRS-6 / scheduler version 6 (unchanged)
- clean-install palette: Ink
- flagship feature: local Anki `.apkg` bridge — any export reads, and each deck's
  language is worked out rather than asked for
