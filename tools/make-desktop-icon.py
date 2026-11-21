#!/usr/bin/env python3
"""Regenerate rounded desktop icons from ikna's committed raster app-icon master.

Desktop builds preserve the supplied rounded icon's alpha mask.
"""

from pathlib import Path
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: python3 -m pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/assets/brand/ikna-app-icon-rounded.png"
SIZES = [256, 128, 64, 48, 32, 24, 16]


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    if source.size != (1024, 1024) or source.getpixel((0, 0))[3] != 0:
        sys.exit("expected a 1024px rounded master with transparency")

    png = ROOT / "desktop/src/main/resources/icon.png"
    source.resize((512, 512), Image.Resampling.LANCZOS).save(png, optimize=True)
    ico = ROOT / "desktop/icon.ico"
    source.save(ico, sizes=[(size, size) for size in SIZES])
    (ROOT / "docs/icon.png").write_bytes(SOURCE.read_bytes())
    print(f"Updated desktop PNG and Windows ICO ({', '.join(str(size) for size in SIZES)})")


if __name__ == "__main__":
    main()
