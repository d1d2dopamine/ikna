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

## Why the numbering restarted at 0.1.0

The pre-epoch `0.x` line (up to `0.6.1`) counted the app being assembled. The
`proof` epoch counts a finished app being hardened, so carrying `0.6.1` forward
would have implied the two scales are comparable. They are not.

One thing does **not** restart: `appVersionCode`. Android refuses to install an
APK whose code is lower than the installed one, and the only irreplaceable thing
in this app is the review log inside that install — so the internal counter keeps
climbing straight across the reset (`proof` starts at `100010000`). The version
**you read** restarted; the version **Android compares** never did.

```
val appVersionName = "0.1.0 proof"
val appVersionCode = 100010000    // epoch offset + major * 100000 + minor * 10000 + patch * 100
```

There is exactly one place where a version number exists: `app/build.gradle.kts`.
Not the tag, because F-Droid builds this repository on a machine that knows nothing
about either tags or CI.

## Tags

Git tags carry the same string with the space turned into a dash, because a tag
cannot contain one: `v0.1.0-proof`. The `release` workflow refuses to publish if
the tag and the build file disagree, so the About line in the app and the file on
the release page cannot drift apart.
