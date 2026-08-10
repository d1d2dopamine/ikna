# Privacy

Ikna has no servers, no accounts and no analytics. There is nothing to opt out
of, because there is nothing collecting anything.

## What leaves your phone

Nothing.

The app has no internet permission at all. Look at
`app/src/main/AndroidManifest.xml`: the only permission requested is
`POST_NOTIFICATIONS`, for the daily reminder. Without `android.permission.INTERNET`
the operating system will refuse a network connection even if some future
dependency tried to open one.

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

That is the whole list.

## Crash reports

There are none. If the app crashes, nobody is told, including us. Open an issue
with what you were doing and, if you can, the log from `adb logcat`.
