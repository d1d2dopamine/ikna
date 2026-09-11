#!/usr/bin/env python3
"""Build the two NSIS bitmaps from ikna's real wordmark.

The installer does not maintain a second logo. Its letters come from the same
alpha artwork Compose tints in the application; the square above the i is drawn
from the same measured proportions and in the default Ink palette's accent.

Outputs:
  desktop/installer/sidebar.bmp  164 x 314, welcome and finish pages
  desktop/installer/header.bmp   150 x 57, directory and progress pages

Run after the wordmark or default palette changes:

    python3 tools/make-installer-art.py

Pillow is used only to author committed artwork. Building the app or installer
does not run Python and does not depend on Pillow.
"""

from pathlib import Path
import argparse
import hashlib
import io
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow is required: python3 -m pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "shared/src/desktopMain/resources/drawable/ikna_wordmark.png"
OUT = ROOT / "desktop/installer"
BACKGROUND = (0xD0, 0xD3, 0xD9)
INK = (0x0E, 0x15, 0x26)
MUTED = (0x48, 0x51, 0x62)
ACCENT = (0x93, 0x2D, 0x19)
DOT_WIDTH = 0.077982
DOT_HEIGHT = 0.198198


def tinted_wordmark(width: int) -> Image.Image:
    source = Image.open(SOURCE).convert("RGBA")
    height = round(width / 2.454955)
    resized = source.resize((width, height), Image.Resampling.LANCZOS)
    alpha = resized.getchannel("A")
    mark = Image.new("RGBA", resized.size, (*INK, 0))
    mark.putalpha(alpha)
    draw = ImageDraw.Draw(mark)
    draw.rectangle(
        (0, 0, max(1, round(width * DOT_WIDTH)) - 1,
         max(1, round(height * DOT_HEIGHT)) - 1),
        fill=(*ACCENT, 255),
    )
    return mark


def pixel_cluster(draw: ImageDraw.ImageDraw, x: int, y: int, cell: int) -> None:
    cells = {
        (0, 0), (1, 0), (2, 0), (4, 0), (5, 0),
        (0, 1), (2, 1), (3, 1), (5, 1),
        (1, 2), (2, 2), (4, 2), (5, 2),
        (0, 3), (3, 3), (4, 3),
        (0, 4), (1, 4), (4, 4),
        (2, 5), (4, 5),
        (2, 6),
    }
    highlights = {(5, 0), (4, 2), (0, 3), (2, 6)}
    for column, row in cells:
        color = ACCENT if (column, row) in highlights else MUTED
        left = x + column * cell
        top = y + row * cell
        draw.rectangle((left, top, left + cell - 1, top + cell - 1), fill=color)


def sidebar() -> Image.Image:
    image = Image.new("RGB", (164, 314), BACKGROUND)
    draw = ImageDraw.Draw(image)
    mark = tinted_wordmark(116)
    image.paste(mark, (22, 38), mark)
    draw.rectangle((22, 111, 115, 113), fill=INK)
    draw.rectangle((22, 119, 91, 121), fill=MUTED)
    draw.rectangle((22, 127, 65, 129), fill=MUTED)
    pixel_cluster(draw, 112, 231, 7)
    return image


def header() -> Image.Image:
    image = Image.new("RGB", (150, 57), BACKGROUND)
    draw = ImageDraw.Draw(image)
    mark = tinted_wordmark(76)
    image.paste(mark, (64, 13), mark)
    pixel_cluster(draw, 8, 8, 5)
    return image


def bmp_bytes(image: Image.Image) -> bytes:
    output = io.BytesIO()
    image.save(output, format="BMP", bits=24)
    return output.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    images = {"sidebar.bmp": sidebar(), "header.bmp": header()}
    changed = []
    for name, image in images.items():
        expected = bmp_bytes(image)
        path = OUT / name
        if args.check:
            if not path.is_file() or path.read_bytes() != expected:
                changed.append(name)
        else:
            path.write_bytes(expected)
            print(f"wrote {path.relative_to(ROOT)} {image.width}x{image.height} "
                  f"sha256={hashlib.sha256(expected).hexdigest()[:16]}")
    if changed:
        sys.exit("installer artwork is stale: " + ", ".join(changed))
    if args.check:
        print("NSIS artwork matches the shared ikna wordmark and Ink palette")


if __name__ == "__main__":
    main()
