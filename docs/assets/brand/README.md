# ikna brand raster assets

These are the committed raster masters used by the repository and application packaging.
They are intentionally raster artwork rather than vector redraws.

- `ikna-banner-en-press.png` — English repository header (`Learn. Discover. Remember.`)
- `ikna-banner-ru-press.png` — Russian repository header (`Учись. Открывай. Помни.`)
- `ikna-app-icon.png` — square application/repository icon master

The dark field is part of the app-icon and banner artwork. The in-app wordmark is
different on purpose: its black presentation field is removed and the supplied
shape is split into two transparent runtime masks, `ikna_wordmark.png` and
`ikna_wordmark_accent.png`. Compose tints those masks with the active palette, so
the letters and the rounded cap above the `i` keep changing with the selected
theme exactly as the previous in-app mark did.

Desktop icon derivatives can be regenerated with `python3 tools/make-desktop-icon.py`.
Installer artwork can be regenerated with `python3 tools/make-installer-art.py`.
