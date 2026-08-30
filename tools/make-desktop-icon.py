#!/usr/bin/env python3
"""Builds the Windows icon from the Android launcher icon.

The desktop application does not get an icon of its own. The mark on the phone
and the mark on the taskbar are the same mark, and the way to keep them that
way is to derive one from the other rather than maintain two files. This script
is the derivation, which is why desktop/icon.ico has no separate source: its
source is the phone icon.

Outputs, both overwritten in place:

  desktop/icon.ico                     what jpackage burns into Ikna.exe, the
                                       Start menu entry and the installer
  desktop/src/main/resources/icon.png  what the running window and the taskbar
                                       show, loaded by Main.kt

Run after the launcher icon changes:

    python3 tools/make-desktop-icon.py

Requires Pillow. Nothing in the Gradle build calls it: an icon changes once a
year at most, and a build step that shells out to Python is a build step that
breaks on somebody else's machine.
"""

import os
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow is required: python3 -m pip install Pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Both layers of the adaptive icon, read from where Android reads them. The
# background is a colour rather than a drawable, so it is a literal here; it has
# to stay in step with ic_launcher_background in values/colors.xml.
BACKGROUND = (0x0B, 0x11, 0x20, 255)
FOREGROUND = os.path.join(
    ROOT, "app", "src", "main", "res", "drawable-nodpi", "ic_launcher_wordmark.png"
)

# An adaptive icon layer is 108dp and a launcher shows the middle 72dp of it;
# the rest is margin for the mask and the parallax. At the 432px the wordmark is
# drawn at, that visible middle is 288px. Taking exactly that crop is what makes
# the desktop icon fill its square the way the phone one does, instead of being
# the same drawing inside a wide unexplained border.
LAYER = 432
VISIBLE = 288

# Windows rounds nothing for you: an icon is drawn exactly as given. 18% is close
# to what Windows 11 does to its own tiles, and to what a round phone mask leaves
# of a square.
CORNER = 0.18

# Every size Windows picks from: 16 in a title bar, 32 on the taskbar, 48 in a
# folder, 256 for the large-icon view and the installer.
SIZES = [256, 128, 64, 48, 32, 24, 16]

SUPERSAMPLE = 8


def load_foreground():
    image = Image.open(FOREGROUND).convert("RGBA")
    if image.size != (LAYER, LAYER):
        sys.exit("expected a %dx%d foreground, found %dx%d" % (LAYER, LAYER, image.size[0], image.size[1]))
    inset = (LAYER - VISIBLE) // 2
    return image.crop((inset, inset, inset + VISIBLE, inset + VISIBLE))


def render(foreground, size):
    """One square icon: rounded plate, wordmark on top, drawn large and reduced."""
    large = size * SUPERSAMPLE
    mask = Image.new("L", (large, large), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, large - 1, large - 1), radius=int(large * CORNER), fill=255
    )

    canvas = Image.new("RGBA", (large, large), (0, 0, 0, 0))
    canvas.paste(Image.new("RGBA", (large, large), BACKGROUND), (0, 0), mask)
    canvas.alpha_composite(foreground.resize((large, large), Image.LANCZOS))

    # The wordmark is composited before the corners are cut, so anything it put
    # outside the plate is removed here rather than left hanging in a corner.
    canvas.putalpha(
        Image.composite(canvas.getchannel("A"), Image.new("L", (large, large), 0), mask)
    )
    return canvas.resize((size, size), Image.LANCZOS)


def main():
    foreground = load_foreground()

    ico = os.path.join(ROOT, "desktop", "icon.ico")
    render(foreground, SIZES[0]).save(ico, format="ICO", sizes=[(s, s) for s in SIZES])

    png = os.path.join(ROOT, "desktop", "src", "main", "resources", "icon.png")
    os.makedirs(os.path.dirname(png), exist_ok=True)
    render(foreground, 512).save(png, format="PNG", optimize=True)

    print("wrote %s (%s)" % (os.path.relpath(ico, ROOT), ", ".join(str(s) for s in SIZES)))
    print("wrote %s (512)" % os.path.relpath(png, ROOT))


if __name__ == "__main__":
    main()
