# ikna v0.12.0 press

> **Know / don't know. We handle the rest.**  
> Local-first flashcards for Android, Windows, and Linux — no accounts, no ads, no tracking.

https://github.com/user-attachments/assets/c90d603c-693f-4c9d-9915-55f04d7c0f5b

---

## Highlights

### Design, Themes & Interface
- **New Frost default palette, Moss/Olive palette & brand pack** — updated default look with rounded controls, refreshed wordmark and app icon, redesigned 12 palette swatches (replacing Cobalt with Moss/Olive), smooth palette color transitions, improved Dusk Graphite grey lighting, and boosted deck-background visibility on the pure-black Zero palette.
- **Animated deck backgrounds** — choose from Gradient, Solid, Glow, Grid, Emblem, Dots, Lines, and Waves with live preview swatches and synchronized emblem animation.
- **Redesigned About Ikna screen** — clear overview of Ikna's core principles (*Local-first and offline*, *No accounts or tracking*, *FSRS-6 and riar-manage*) plus quick links to GitHub, Issues, Email, Profile, and Website.
- **Polished review & desktop navigation** — smoother mobile card swipe exit with clipped bounds, compact floating Undo capsule after answering, unified full-width desktop bottom navigation bar, geometric icons across Settings and panes, smooth gliding Settings tab underline, and animated waiting lattice indicators.
- **First-launch onboarding & reading cat** — cleaner step-by-step onboarding with animated pill progress dots and Back navigation; reading cat moved into the Today card with crisp downscaling.
- **Tactile sound cues & haptics** — subtle, warm UI feedback sounds for card actions and navigation with reliable Android audio playback (toggleable in Settings).

### Profile, Study & Analytics
- **Local Profile & Deep Analytics** — the Statistics tab is now **Profile**, featuring a custom local avatar photo (`+`), editable display name and study focus, memory maturity breakdown, 12-week square activity grid, 7-day due forecast, active deck progress, and one-tap **1200×720 PNG study snapshot** export in the active palette.
- **Optional hard-card review** — take a finite extra batch of difficult cards after finishing your daily required plan, while keeping required-plan credit and FSRS scheduling intact.
- **Study statistics export** — export your raw study journal alongside recall and due-date visibility.
- **Restore hidden cards individually** — unhide specific cards directly from deck details.
- **Better session & empty states** — contextual finish messages, returning-learner welcome, clean empty-library state, monotonic review duration bounds, and open-session safety after sync.

### Decks, AI (MCP) & Local Sync
- **Ikna MCP setup in-app** — collapsible *Connect via Ikna MCP* panel inside *Add deck → The model way* to prepare, review, and atomically import AI-generated decks locally.
- **Offline language detection** — automatically detects deck language on plain-text import.
- **Friendly serverless sync (LAN QR + `.ikna-sync` files)** — built-in Android QR camera that connects directly inside Ikna (no external browser), Level-H branded QR codes with the Ikna emblem, real device names instead of raw IPs, modal merge / choose-device dialog, raised `16 MiB / 200,000 rows` initial profile transfer limit, Wi-Fi subnet priority, hardened Windows NIO ACL private sync storage, and offline `.ikna-sync` GZIP+SHA-256 delta file export/merge.

### Desktop & Installers
- **Redesigned desktop update card & modal dialog** — platform-specific release downloads right from Settings and the deck list.
- **Updated Windows installer** — vertical Ikna brand banner in the NSIS installer with automated silent install/uninstall smoke tests.

---

## Downloads

| Platform | File |
| --- | --- |
| Android 10+ (arm64) | `ikna-android-arm64.apk` |
| Android 10+ (32-bit ARM) | `ikna-android-legacy32.apk` |
| Windows x64 | `ikna-windows-x64-setup.exe` (Installer) · `ikna-windows-x64-portable.zip` (Portable) |
| Linux x86_64 | `ikna-linux-x86_64.AppImage` |

---

**Full Changelog:** https://github.com/d1d2dopamine/ikna/compare/v0.11.0-press...v0.12.0-press
