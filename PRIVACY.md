# Privacy

Ikna has no servers, no accounts and no analytics. There is nothing to opt out
of, because there is nothing collecting anything.

## What leaves your phone

Nothing about you.

The app makes requests, and all of them are the same shape: a GET for a file that
is public anyway, with no body, no account, no identifier and no card in it.

| Request | When | What is asked for |
| --- | --- | --- |
| The update check | Once a day, and only if the switch in Settings is on | The releases page's latest tag |
| The update download | Only when you press the button in that window | The APK of that release |
| The catalogue index | Only when you open the catalogue screen | One small list of ready-made decks |
| A catalogue deck | Only when you tap a deck in that list | That deck's file |

Nothing is uploaded in any of them, and nothing is sent to us, because there is no
us to send it to: those files sit on the releases page of this repository, and the
request looks exactly like a browser downloading them. See
[`docs/UPDATES.md`](docs/UPDATES.md) and [`docs/SOURCES.md`](docs/SOURCES.md).

With the update switch off, the app never opens a socket on its own — the only
remaining requests are the two the catalogue makes, and both of them happen
because you pressed something.

## What is stored, and where

| Data | Where it lives |
| --- | --- |
| Your answers, card schedules, word-level state, daily counts | `ikna.db`, in the app's private storage |
| Settings: theme, colours, font, language, reminder time, voice | the app's private storage |
| A font you imported | copied into the app's private storage |
| Weekly export of the review log and settings | `Documents/ikna/`, on your own device |
| Widget text | the app's private storage |

The export in `Documents/ikna/` is the one thing written outside the sandbox, and
it is written there deliberately: files inside the sandbox are deleted with the
app, and the point of that export is to survive an uninstall and a new phone. It
goes nowhere else. Copying it off the device — or not — is your decision.

## Cloud backup

Android can copy an app's data into your Google account automatically. Ikna turns
that off (`android:allowBackup="false"`), and `res/xml/data_extraction_rules.xml`
excludes `ikna.db` from cloud backup explicitly. Your review log — every phrase
you studied, how you rated it and when — is not uploaded anywhere, including by
the operating system on your behalf.

Direct device-to-device transfer, the copy Android makes during setup when you
move to a new phone, is still allowed: it goes straight to the new device with no
server in between. Settings are also included in cloud backup, since they are
preferences rather than a record of what you have been reading.

The deliberate way to move your history is the export in `Documents/ikna/`, which
you control.

## Speech

Reading a chunk aloud uses a speech engine installed on your phone, through the
platform's own text-to-speech interface. Ikna does not ship a voice, does not
download one, and does not send text to any online service. Which engine you have
installed, and what that engine does, is between you and its author; a fully
offline engine is the reason this feature is built the way it is.

The app asks the system which speech services are installed
(`<queries>` in the manifest) purely so it can tell whether the speak mark should
appear at all.

## Permissions

- **Notifications** — the daily reminder. Refuse it and the rest of the app works
  unchanged.
- **Internet** — the four requests in the table above, and nothing else. Turn the
  update check off and stay out of the catalogue, and it is never used.
- **Requesting an install** — the right to hand a downloaded APK to the system
  installer, which then shows its own prompt and checks the signature itself. It
  installs nothing on its own, and Android makes you allow it per app.

That is the whole list, and each one is explained where it is declared, in
`app/src/main/AndroidManifest.xml`.

## Crash reports

There are none. If the app crashes, nobody is told, including us. Open an issue
with what you were doing and, if you can, the log from `adb logcat`.
