# ikna brand raster assets

These are the committed raster masters used by the repository and application packaging.
They are intentionally raster artwork rather than vector redraws.

- `ikna-banner.png` — shared repository header used by both the English and Russian README sections
- `ikna-wordmark-master.png` — high-resolution transparent wordmark master
- `ikna-icon-background.png` — clean patterned icon background without lettering
- `ikna-app-icon.png` — 2048px square application icon master assembled from those two rasters

The in-app wordmark is split into two high-resolution transparent alpha masks,
`ikna_wordmark.png` and `ikna_wordmark_accent.png`. Compose tints the letter mask
with the active interface ink and the cap with the active primary colour, so the
logo continues to follow the selected palette. The masks retain antialiased edge
coverage from the supplied raster instead of using a hard background threshold.

Android uses a separate transparent launcher foreground and patterned background.
The foreground is optically centred inside the adaptive safe zone, so launcher
masks cannot crop or shift the wordmark. Pre-Android-8 launcher images are rendered
as rounded/circular raster icons. Windows and Linux use the same master with an
antialiased rounded-square alpha mask instead of a raw square plate.

Regenerate all raster derivatives with:

```bash
python3 tools/make-brand-assets.py
```

Desktop icons alone can be regenerated with `python3 tools/make-desktop-icon.py`.
Installer artwork can be regenerated with `python3 tools/make-installer-art.py`.
