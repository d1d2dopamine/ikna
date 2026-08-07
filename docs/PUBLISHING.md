# Publishing

Three places, in the order they are worth doing.

## 1. GitHub Releases (already working)

Bump `appVersionName` / `appVersionCode` in `app/build.gradle.kts`, tag the
commit with the same number, push the tag. The `release` workflow checks that
the tag and the build file agree, runs the tests, builds a signed APK and
attaches `ikna-v<version>.apk` to the release page.

This is the source everything else feeds on. Nothing below works if a release
does not carry an APK asset.

## 2. IzzyOnDroid (recommended next step)

An F-Droid-compatible repository that does **not** build anything. It takes the
APK you already published on GitHub, scans it, and lists it. People add
`https://apt.izzysoft.de/fdroid/repo` in their F-Droid client and get updates
from there.

Why it fits this project better than the main F-Droid repository:

- **The APK stays signed with our key.** An update from IzzyOnDroid installs
  straight over a build downloaded from GitHub. Nobody has to uninstall, and no
  review history is lost. This is the entire reason to prefer it.
- **Days, not weeks.** Inclusion is a single issue and usually a short
  conversation.
- **The listing comes from this repository.** Same `fastlane/metadata/android/`
  files F-Droid would read: title, descriptions, per-version changelogs, icon,
  screenshots.
- Updates are automatic afterwards. Their bot watches the releases; a new tag is
  all it takes.

What they check before accepting: a free license (GPL-3.0-or-later here), public
source, no proprietary or tracking libraries, and an APK scan for known trackers
and malware signatures. This app has no network permission at all, so there is
nothing for a tracker scan to find.

### Submitting

1. Push the tag and make sure the release page carries the APK.
2. Open an issue in `gitlab.com/IzzyOnDroid/repo` using the inclusion request
   template. A GitLab account is the only registration involved.
3. Give the repository URL. Everything else - description, changelog, icon - is
   read out of `fastlane/metadata/android/en-US/`.
4. Answer whatever the maintainer asks. Common question for this project: why a
   keystore is committed. The answer is in `docs/KEYSTORE.md`, and it does not
   affect them - they take the finished APK, they do not build it.

### Keeping the listing fed

One file per release: `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
For 0.3.1 that is `30100.txt`. A missing file is not an error; the entry simply
shows nothing.

Screenshots go in `fastlane/metadata/android/en-US/images/phoneScreenshots/` as
`1.png` to `4.png`. The icon is already there as `images/icon.png`, rendered from
the app's own launcher vector.

## 3. F-Droid main repository (optional, later)

Same store, stricter path: their servers clone this repository at a tag and
build it themselves, then sign it with **their** key.

The cost of that is real: an APK signed by F-Droid is, to Android, a different
app than one signed by `ikna.keystore`. Switching from a GitHub or IzzyOnDroid
install means uninstalling first, which deletes the review history unless it is
exported and restored. Reviews are also slow - weeks.

What is already in place for it, should it ever be wanted:

- The version is declared in `app/build.gradle.kts` instead of being derived from
  a CI run number, which does not exist on their machines.
- `-Pikna.unsigned=true` disables the committed signing config, so their build
  comes out unsigned as they require.
- The same `fastlane/` metadata is read by both repositories.

The submission is a merge request against `gitlab.com/fdroid/fdroiddata` adding
`metadata/dev.ikna.yml`:

```yaml
Categories:
  - Science & Education
License: GPL-3.0-or-later
SourceCode: https://github.com/<account>/ikna
IssueTracker: https://github.com/<account>/ikna/issues
Changelog: https://github.com/<account>/ikna/blob/main/CHANGELOG.md

AutoName: Ikna

RepoType: git
Repo: https://github.com/<account>/ikna.git

Builds:
  - versionName: 0.3.0
    versionCode: 30000
    commit: v0.3.0
    subdir: app
    gradle:
      - yes
    gradleprops:
      - ikna.unsigned=true

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.3.0
CurrentVersionCode: 30000
```

Or, with less work and a longer wait, open a request at `gitlab.com/fdroid/rfp`
and let a volunteer write that file.

## Obtainium

Worth mentioning in the README for people who want neither: Obtainium installs
and updates apps directly from GitHub releases. Nothing is required on this side.
