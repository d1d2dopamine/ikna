# Ikna brand assets

These files come from the supplied Ikna brand pack.

| Asset | Where to use it |
| --- | --- |
| [README banner](ikna-banner.png) | GitHub repository header, 1600 × 500 |
| [Link preview](ikna-link-preview.png) | GitHub Social Preview, 1200 × 630 |
| [Square banner](ikna-banner-square.png) | Square posts, 1080 × 1080 |
| [Vertical banner](ikna-banner-vertical.png) | Vertical posts, 1080 × 1920 |
| [X header](ikna-banner-x.png) | X profile header, 1500 × 500 |
| [Square app icon](ikna-app-icon.png) | Android launcher and splash source |
| [Rounded app icon](ikna-app-icon-rounded.png) | Windows, Linux and Android launcher source |
| [Light wordmark](ikna-wordmark-master.png) | Dark surfaces and the in-app tint masks |
| [Dark wordmark](ikna-wordmark-dark.png) | Light surfaces |

The Android adaptive icon uses a solid plate and a transparent mark inside the
launcher's mask. The in-app wordmark uses separate tint masks for its letters and
accent. Windows ICO embeds seven resolutions derived from the rounded master.

To regenerate the packaged assets after changing a master, run these commands
with Pillow installed.

```bash
python3 tools/make-brand-assets.py
python3 tools/make-desktop-icon.py
python3 tools/make-installer-art.py
```
