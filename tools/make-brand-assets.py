#!/usr/bin/env python3
"""Regenerate Android, desktop and in-app artwork from the committed Ikna masters."""

from pathlib import Path
import sys

try:
    from PIL import Image, ImageChops, ImageDraw
except ImportError:
    sys.exit("Pillow is required: python3 -m pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "docs" / "assets" / "brand"
WORDMARK = BRAND / "ikna-wordmark-master.png"
APP_MASTER = BRAND / "ikna-app-icon.png"
ROUNDED_MASTER = BRAND / "ikna-app-icon-rounded.png"

ADAPTIVE_SIZE = 432
# The artwork is scaled together so its furthest corner stays within Android's
# central circular safe zone. The separate solid background fills the launcher mask.
ADAPTIVE_ART_SIZE = 336
LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
ICO_SIZES = [256, 128, 64, 48, 32, 24, 16]


def load_masters() -> tuple[Image.Image, Image.Image, Image.Image]:
    mark = Image.open(WORDMARK).convert("RGBA")
    square = Image.open(APP_MASTER).convert("RGB")
    rounded = Image.open(ROUNDED_MASTER).convert("RGBA")
    if square.size != (1024, 1024) or rounded.size != square.size:
        sys.exit("expected matching 1024px square and rounded icon masters")
    return mark, square, rounded


def circular_icon(source: Image.Image, size: int) -> Image.Image:
    supersample = size * 8
    mask = Image.new("L", (supersample, supersample))
    ImageDraw.Draw(mask).ellipse((0, 0, supersample - 1, supersample - 1), fill=255)
    icon = source.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    icon.putalpha(mask.resize((size, size), Image.Resampling.LANCZOS))
    return icon


def write_dynamic_wordmark_masks(mark: Image.Image) -> None:
    # The transparent master has already been decontaminated from its old black
    # presentation field. Classifying its visible pixels by colour gives two
    # antialiased masks with no dark fringe for ColorFilter.tint to expose.
    px = mark.load()
    letters = Image.new("RGBA", mark.size, (255, 255, 255, 0))
    accent = Image.new("RGBA", mark.size, (255, 255, 255, 0))
    lp = letters.load()
    ap = accent.load()
    for y in range(mark.height):
        for x in range(mark.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # The accent is the rounded cap above the i and lives only in the
            # top-left of the supplied wordmark. Restricting colour classification
            # to that region prevents faint antialias pixels on the pale letters
            # from being amplified into stray accent specks.
            is_accent = (
                x < mark.width * 0.16
                and y < mark.height * 0.38
                and g > r * 1.15
                and b > r * 1.15
            )
            if is_accent:
                ap[x, y] = (255, 255, 255, a)
            else:
                lp[x, y] = (255, 255, 255, a)

    targets = [
        ROOT / "shared" / "src" / "androidMain" / "res" / "drawable-nodpi",
        ROOT / "shared" / "src" / "desktopMain" / "resources" / "drawable",
    ]
    for folder in targets:
        folder.mkdir(parents=True, exist_ok=True)
        letters.save(folder / "ikna_wordmark.png", "PNG", optimize=True)
        accent.save(folder / "ikna_wordmark_accent.png", "PNG", optimize=True)


def write_android(square: Image.Image, rounded: Image.Image) -> None:
    resources = ROOT / "app/src/main/res"
    drawables = resources / "drawable-nodpi"
    background_color = square.getpixel((0, 0))
    uniform = Image.new("RGB", square.size, background_color)
    alpha = ImageChops.difference(square, uniform).convert("L").point(lambda value: 255 if value else 0)
    artwork = square.convert("RGBA")
    artwork.putalpha(alpha)
    art = artwork.resize((ADAPTIVE_ART_SIZE, ADAPTIVE_ART_SIZE), Image.Resampling.LANCZOS)
    foreground = Image.new("RGBA", (ADAPTIVE_SIZE, ADAPTIVE_SIZE))
    inset = (ADAPTIVE_SIZE - ADAPTIVE_ART_SIZE) // 2
    foreground.alpha_composite(art, (inset, inset))
    foreground.save(drawables / "ic_launcher_wordmark.png", optimize=True)
    Image.new("RGB", foreground.size, background_color).save(
        drawables / "ic_launcher_background_art.png", optimize=True
    )

    for folder, size in LEGACY.items():
        out = resources / folder
        rounded.resize((size, size), Image.Resampling.LANCZOS).save(out / "ic_launcher.png", optimize=True)
        circular_icon(square, size).save(out / "ic_launcher_round.png", optimize=True)


def main() -> None:
    mark, square, rounded = load_masters()
    write_dynamic_wordmark_masks(mark)
    write_android(square, rounded)
    print(f"Updated Android icons and wordmark masks ({mark.width}x{mark.height})")


if __name__ == "__main__":
    main()
