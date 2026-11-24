# ikna 0.13.0 press

> **Know / don't know. We handle the rest.**  
> Local-first flashcards for Android, Windows, and Linux — no accounts, no ads, no tracking.

---

## Highlights

### Local Profile & Open-Canvas Analytics
- **Profile screen (formerly Statistics)** — clean editorial layout with a custom local avatar photo (`+`), editable display name and study focus, open typographic metrics strip, and balanced side-by-side 12-week activity grid, memory maturity breakdown, and 7-day due forecast.
- **Calm deck progress** — replaced intimidating `0/3000` denominators and empty progress bars with quiet positive status (`mature`, `in progress`, or `Ready`).
- **Unified Profile & Journal** — legacy 7-day / 30-day review journal tables and CSV/JSON export are tucked under a collapsible *Show detailed journal* toggle to keep the Profile screen focused.
- **Redesigned 1200×720 PNG snapshot card** — open-canvas export on Desktop and Android in the active palette with a circular avatar, single date stamp, 1:1 square 12-week heatmap, measured single-line legend, calm deck rows, and no empty `recovered: 0` clutter.

### Serverless Device Sync (LAN QR & `.ikna-sync`)
- **Seamless cross-device & cross-deck sync** — devices no longer need identical local decks or matching pack versions to sync; protocol negotiation supports v3 and v4 peers, incompatible extra packs are safely filtered during adoption, and `.ikna-sync` delta merges skip unknown targets without failing.
- **In-app Android QR scanner & branded QR codes** — scan directly inside Ikna (no external browser), Level-H QR codes with the Ikna emblem, real device names in the modal merge / choose-device dialog, Wi-Fi `192.168.*` address priority, 30-second exchange timeout, raised `16 MiB / 200,000 rows` transfer limit, and clear DEV-sandbox isolation notice.

### Catalogue v2 Structure
- **Three-collection Catalogue tabs** — added *Everyday*, *Knowledge*, and *World* collections to the Catalogue screen on Android and Desktop (across all 7 interface languages), with *Everyday* active and clear status placeholders for *Knowledge* and *World* while their source verification completes.

### Visual Polish & Desktop Window
- **Seamless Desktop window overlay** — removed the full-width empty top bar and horizontal divider line on Windows; the app now fills the full window height while keeping compact top-right window controls and top-edge drag support.
- **Moss/Olive palette & pure-black Zero contrast** — replaced Cobalt with **Moss/Olive**, added smooth palette crossfades, and boosted deck background visibility on the pure-black Zero dark palette.
- **Mobile & update improvements** — smoother card swipe exit with clipped bounds, reliable Android UI sound playback, and redesigned Desktop and Android update dialogs.

---

## Downloads

| Platform | File |
| --- | --- |
| Android 10+ (arm64) | `ikna-android-arm64.apk` |
| Android 10+ (32-bit ARM) | `ikna-android-legacy32.apk` |
| Windows x64 | `ikna-windows-x64-setup.exe` (Installer) · `ikna-windows-x64-portable.zip` (Portable) |
| Linux x86_64 | `ikna-linux-x86_64.AppImage` |

---

**Full Changelog:** https://github.com/d1d2dopamine/ikna/compare/v0.12.0-press...v0.13.0-press
