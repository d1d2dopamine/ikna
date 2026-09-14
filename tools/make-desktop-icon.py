#!/usr/bin/env python3
"""Regenerate desktop icons from the committed raster application icon.

The brand master is a PNG supplied as artwork; it is not traced or rebuilt as
vectors. Linux/Compose uses a 512 px PNG. Windows requires an ICO container, so
that file is made from raster resizes of the exact same PNG.

Outputs:
  desktop/icon.ico
  desktop/src/main/resources/icon.png

Run after docs/assets/brand/ikna-app-icon.png changes:

    python3 tools/make-desktop-icon.py
"""

from pathlib import Path
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: python3 -m pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "assets" / "brand" / "ikna-app-icon.png"
SIZES = [256, 128, 64, 48, 32, 24, 16]


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    if source.width != source.height or source.width < 512:
        sys.exit(f"expected a square raster master >=512 px, found {source.width}x{source.height}")

    png = ROOT / "desktop" / "src" / "main" / "resources" / "icon.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    source.resize((512, 512), Image.Resampling.LANCZOS).save(png, "PNG", optimize=True)

    ico = ROOT / "desktop" / "icon.ico"
    source.resize((256, 256), Image.Resampling.LANCZOS).save(
        ico, "ICO", sizes=[(size, size) for size in SIZES]
    )

    print(f"wrote {ico.relative_to(ROOT)} ({', '.join(str(s) for s in SIZES)})")
    print(f"wrote {png.relative_to(ROOT)} (512)")


if __name__ == "__main__":
    main()
