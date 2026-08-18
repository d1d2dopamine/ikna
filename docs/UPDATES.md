# Updates

This app is not in a store. A build reaches a phone as an APK from the releases
page, which has one consequence that took four versions to become obvious: a
person running a version with a known bug in it has no way of finding out. There
is nothing to notify them. `0.3.0 press` shipped with a paste bug that was fixed
the same evening, and the only reason anybody knew is that they asked.

So the app now asks, and this file is the whole of what that means.

## What it does

One HTTPS GET, to the releases API of this repository:

```
https://api.github.com/repos/d1d2dopamine/ikna/releases/latest
```

- On opening the app, and **at most once a day**. The day stamp is written whether
  or not anything came back, so a phone with no network does not retry on every
  launch.
- Or when **Check now** is pressed in Settings -> Updates.
- The reply is a few kilobytes of JSON: the tag, the size of each attached file,
  and the release notes. The notes shown in the window are that text, tidied:
  the badge block, the rules and the link syntax are dropped and the rest is cut
  to about 1400 characters.
- Which of the two APKs is offered depends on the phone: one without a 64-bit ABI
  is offered the 32-bit file, because the speech runtime is native code and the
  wrong one installs and then fails at the first sentence.

## What it does not do

- **Nothing is sent.** The request has no body and carries no account, no
  identifier, no statistics, no card and no answer. The only thing it discloses
  that a browser would not is the version it is running, which is in the user
  agent so that a broken release can be recognised, not so anybody can be
  counted. There is no analytics service, and adding one is out of scope for this
  project rather than merely unimplemented.
- **Nothing is downloaded before the button is pressed.** The check is a few
  kilobytes of JSON. The APK is fetched only after `Update`, and cancelling stops
  the socket rather than the display.
- **Nothing is installed by the app.** The file is handed to the system
  installer, and the prompt that names the app, says it is an update and checks
  the signature is the platform's. `REQUEST_INSTALL_PACKAGES` is the right to
  ask; the answer is not ours to give.
- **The review log still never leaves the phone.** It is exported to
  `Documents/ikna/` by the user, on purpose, and `allowBackup` remains off. See
  the comment in `AndroidManifest.xml`.
- **Nothing is ever installed silently**, and there is no background worker for
  this. If the app is not open, it is neither checking nor downloading.

## The window

What is available, in the order the questions actually arrive:

```
UPDATE AVAILABLE
0.3.0 press  ->  0.4.0 press
Size: 40.2 MB

What is new:
<the release notes, scrolling inside a fixed box>

                              SKIP   UPDATE
```

The installed version comes first because the question a person has is not
only what is new but how far behind they are.

## Pressing update

The two words are replaced by the download, in the same window:

```
DOWNLOADING  47%
[################                    ]
18.9 / 40.2 MB

                                      CANCEL
```

- The file goes into the app's own cache, under `updates/`, and it is the only
  thing in there: a previous attempt is cleared before the next one starts.
  An interrupted download is never resumed and never handed on -- a short file
  that kept its name would be refused by the installer, which reads as "the
  update is broken" rather than "the network dropped".
- The percentage is the bytes written against the length the server declared,
  falling back to the size the release listed. It cannot reach 100 before the
  file is whole, so "100%" and "the installer is opening" are one moment.
- While bytes are arriving, a tap outside the window does nothing. A download
  cancelled by a misplaced finger at eighty percent is the worst thing this
  window could do; **Cancel** is there and is the only way.
- When it is done the system installer opens by itself. It puts the new version
  **over** the old one, because every release is signed with the same key, so the
  review log, the decks and the settings are all still there afterwards. See
  [`docs/KEYSTORE.md`](KEYSTORE.md).
- Android requires permission to install apps from this source, per app, on a
  settings screen. It is asked for at the moment it is needed -- file already
  downloaded, window saying what it is for -- and never on first launch. Granting
  it and pressing **Install** again costs no second download.
- If the download fails, the window says so and offers **Retry** and **In the
  browser**. The app fetching the file is a convenience; it never becomes the
  only way to get it.

**Skip** silences that one version. The release after it asks again -- a skip is
not a preference about updates, it is an answer about this one. Tapping outside
the window behaves like skip without remembering it, which is the right reading of
a tap that may not have been aimed at anything.

## Changing your mind

**Settings -> Updates** holds the same check on demand, and it ignores the skip:
pressing the button *is* the change of mind. The section also shows which version
is installed, which one was skipped, the switch that turns the check off
altogether, and a link to the releases page for when the check itself cannot work
-- no network, a rate limit, or simply wanting to read the whole release.

A failed request and an up-to-date install both arrive as nothing, and the app
cannot honestly tell them apart, so the line it shows says both.

## Switched off

With the switch off no socket is opened at all: the check is the only network
code in the app, and it is not reached. The window cannot appear, and the button
in Settings is the only way to ask.

## Where the code is

| File | What is in it |
| --- | --- |
| `data/update/UpdateLogic.kt` | Comparing versions, choosing the APK, tidying the notes. Pure functions, covered by `UpdateLogicTest`. |
| `data/update/UpdateCheck.kt` | The request itself: short timeouts, a capped reply, every failure returning null. |
| `data/update/UpdateDownload.kt` | Fetching the APK into the cache, the percentage arithmetic, the file name, and handing the file to the installer. Pure parts covered by `UpdateDownloadTest`. |
| `ui/update/UpdateSheet.kt` | The window, and the gate that decides whether to show it. |
| `ui/update/UpdateDownloadUi.kt` | The band, the percentage and the buttons, shared by the window and Settings. |
| `ui/settings/SettingsScreen.kt` | The Updates section. |

Version comparison ignores the epoch word: `0.3.0 press` against `v0.4.0-press`
is three numbers against three numbers, so a `proof` tag left on the page cannot
pull a `press` install sideways. An unreadable version on either side means no
update -- an app that cannot tell must not be able to nag.
