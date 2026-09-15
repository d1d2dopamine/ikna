#!/usr/bin/env python3
"""Regenerate rounded desktop icons from ikna's committed raster app-icon master.

The source is a 2048px raster composition. Desktop outputs keep the exact artwork
but add an antialiased rounded-square alpha mask, so Windows/Linux launchers do
not show a raw square plate around the icon.
"""

from pathlib import Path
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow is required: python3 -m pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "assets" / "brand" / "ikna-app-icon.png"
SIZES = [256, 128, 64, 48, 32, 24, 16]
CORNER = 0.22


def rounded(source: Image.Image, size: int) -> Image.Image:
    image = source.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    ss = 8
    mask = Image.new("L", (size * ss, size * ss), 0)
    draw = ImageDraw.Draw(mask)
    radius = round(size * CORNER * ss)
    draw.rounded_rectangle((0, 0, size * ss - 1, size * ss - 1), radius=radius, fill=255)
    image.putalpha(mask.resize((size, size), Image.Resampling.LANCZOS))
    return image


def main() -> None:
    source = Image.open(SOURCE).convert("RGB")
    if source.width != source.height or source.width < 1024:
        sys.exit(f"expected a square raster master >=1024 px, found {source.width}x{source.height}")

    png = ROOT / "desktop" / "src" / "main" / "resources" / "icon.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    rounded(source, 512).save(png, "PNG", optimize=True)

    ico = ROOT / "desktop" / "icon.ico"
    rounded(source, 256).save(ico, "ICO", sizes=[(size, size) for size in SIZES])

    print(f"wrote {ico.relative_to(ROOT)} ({', '.join(str(s) for s in SIZES)})")
    print(f"wrote {png.relative_to(ROOT)} (512, rounded raster)")


if __name__ == "__main__":
    main()
