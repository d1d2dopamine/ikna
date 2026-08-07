# Screenshots

The README expects four files in this folder, with these exact names:

| File | What to capture |
| --- | --- |
| `decks.png` | The deck screen, with both decks visible and something due today |
| `session.png` | A card mid-session, front side, so the top row and the estimate are in shot |
| `stats.png` | The statistics screen, scrolled to the top |
| `settings.png` | The settings screen, scrolled to the top |

## Taking them

Just the phone's own screenshot button. No frames, no drop shadows, no phone
mock-ups: the interface is flat and square, and a rounded plastic bezel around it
is a different design speaking over ours.

A few things worth doing before you press the button:

- **Dark theme.** It is what the app opens in and what the widget matches.
- **Real content.** A deck with actual progress reads as a working app; an empty
  one reads as a mock-up.
- **Nothing personal in the notification bar.** It is in every shot.

## Size

Scale them down to about 1080px wide before committing. A modern phone screenshot
is two or three megabytes, four of those make the repository noticeably heavier to
clone, and GitHub scales them down for display anyway.

```
for f in *.png; do sips --resampleWidth 1080 "$f"; done      # macOS
mogrify -resize 1080x *.png                                  # ImageMagick
```

Until the files are here, the images in the README will show as broken links.
