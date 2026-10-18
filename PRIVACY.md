# Privacy

ikna has no hosted account service and no analytics. Optional paired-device
synchronization uses a local listener only while the application is active and
you have enabled the feature. It is off by default.

## What leaves your device

Public downloads use ordinary requests without your review history:

| Request | When | What is transferred |
| --- | --- | --- |
| Update check | When enabled, at most daily, or explicitly requested | Public release metadata |
| Update download | When requested | Release APK |
| Catalogue index/deck | When opened or selected | Public catalogue files |
| Paired local sync | After explicit enablement and pairing, while apps are active | Encrypted installed-deck/card/context content and active review/undo journal for preview; after mutual approval, compatible content and learning changes |

Synchronization does not send data to an Ikna server. It sends personal learning
history and installed card content to the device you explicitly pair. Pairing authorizes the preview transfer; both devices must additionally approve the exact proposal before learning data changes. Treat pairing invitations as secrets;
do not publish them or include them in bug reports. Disabling updates does not
disable separately enabled synchronization. Disable sync or revoke pairing in
Settings to stop it. See [sync scope and recovery](docs/SYNC-PROFILES-0.12.md),
[updates](docs/UPDATES.md) and [sources](docs/SOURCES.md).

## What is stored, and where

| Data | Where it lives |
| --- | --- |
| Your answers, card schedules, word-level state, daily counts | `ikna.db` or the selected UUID-named private profile database |
| Settings: theme, colours, font, language and platform options | ikna's data directory |
| A font you imported | copied into ikna's data directory |
| Android review/settings export | `Documents/ikna/`, on your own device |
| Desktop data | `%APPDATA%\Ikna` on Windows; `~/.ikna` on Linux |
| Android widget text | Android private storage |
| Pairing certificate/private key, peer credentials and local sync configuration | Private `device-sync` directory; excluded from ordinary exports and Android transfer rules |

On Android, the export in `Documents/ikna/` is deliberately outside the app
sandbox so it can survive an uninstall and a new phone. On desktop, cards and
settings live outside the program files and a complete portable backup can be
written to a location you choose. Ordinary exports are not automatically uploaded. If you separately enable paired
sync, profile content and the active review journal are exchanged with the paired device as described above.

Profile adoption retains the previous private database for explicit recovery. It does not create a backup of device settings or media. The explicit erase-everything action also clears retained and unfinished profile databases, with partial failures reported. Its automatic export covers only the current profile. Nonsecret slot origin metadata remains; it contains no learner content and grants no sync permission. This is application-level deletion, not a forensic secure-erasure guarantee. Database selection uses a separate nonsecret SQLite journal.

## Android cloud backup

Android can copy an app's data into your Google account automatically. Ikna turns
that off (`android:allowBackup="false"`), and `res/xml/data_extraction_rules.xml`
excludes `ikna.db` from cloud backup explicitly. These rules do not include the review log in cloud backup. Optional paired LAN
sync is a separate explicit transfer of learning history.

Direct device-to-device transfer, the copy Android makes during setup when you
move to a new phone, is still allowed: it goes straight to the new device with no
server in between. The extraction rules list shared preferences for cloud backup; actual OS behavior
also depends on the manifest and platform. The transfer rules include the legacy database and the dedicated `sync-profiles` database directory, including retained recovery databases and SQLite sidecars. Pairing keys and consent configuration are not included; a new pairing and preview are required. Actual operating-system transfer has not been verified on physical devices.

You can also move history through an explicit export in `Documents/ikna/` or the
paired local synchronization controls. Reset-generation recovery rules are
described in the sync documentation.

## Speech

On Android, reading a chunk aloud uses a speech engine installed on your phone, through the
platform's own text-to-speech interface. Ikna does not ship a voice, does not
download one, and does not send text to any online service. Which engine you have
installed, and what that engine does, is between you and its author; a fully
offline engine is the reason this feature is built the way it is.

The app asks the system which speech services are installed
(`<queries>` in the manifest) purely so it can tell whether the speak mark should
appear at all.

## Android permissions

- **Notifications** — the daily reminder. Refuse it and the rest of the app works
  unchanged.
- **Internet** — public downloads and the separately enabled authenticated local
  synchronization listener/exchanges described above.
- **Requesting an install** — the right to hand a downloaded APK to the system
  installer, which then shows its own prompt and checks the signature itself. It
  installs nothing on its own, and Android makes you allow it per app.

That is the whole list, and each one is explained where it is declared, in
`app/src/main/AndroidManifest.xml`.

## Crash reports

There are none. If the app crashes, nobody is told, including us. Open an issue
with what you were doing and, if you can, the log from `adb logcat`.

## Local Anki import

Anki packages selected for import are copied only into ikna's private cache for parsing and are deleted after the attempt. The original document is not modified. Card text, deck names, review history and compatibility reports are not uploaded by the importer. Imported scripts and styles are discarded rather than executed. Android's document picker remains the only file-access boundary.
