#!/usr/bin/env python3
"""Regenerate ikna's raster brand derivatives from committed raster masters.

Nothing here traces or rebuilds the supplied lettering as vectors. The repository
keeps two raster masters under docs/assets/brand:

  ikna-wordmark-master.png    full-colour wordmark, transparent background
  ikna-icon-background.png    dark patterned square background without lettering

This script combines them at high resolution, then derives platform assets with
proper safe-zone placement and masks. The in-app wordmark is split into two alpha
masks so Compose can tint the letters and the warm cap with the active palette.
"""

from pathlib import Path
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:
    sys.exit("Pillow is required: python3 -m pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "docs" / "assets" / "brand"
WORDMARK = BRAND / "ikna-wordmark-master.png"
BACKGROUND = BRAND / "ikna-icon-background.png"
APP_MASTER = BRAND / "ikna-app-icon.png"

APP_SIZE = 2048
APP_WORDMARK_WIDTH = 1220
APP_OPTICAL_Y = 10
ADAPTIVE_SIZE = 432
ADAPTIVE_WORDMARK_WIDTH = 252
ADAPTIVE_OPTICAL_Y = 2
LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
ICO_SIZES = [256, 128, 64, 48, 32, 24, 16]


def load_masters() -> tuple[Image.Image, Image.Image]:
    mark = Image.open(WORDMARK).convert("RGBA")
    bg = Image.open(BACKGROUND).convert("RGB")
    if mark.width < 1000 or mark.height < 300:
        sys.exit(f"wordmark master is unexpectedly small: {mark.width}x{mark.height}")
    if bg.width != bg.height or bg.width < 1024:
        sys.exit(f"icon background must be a square raster >=1024 px: {bg.width}x{bg.height}")
    return mark, bg


def resize_mark(mark: Image.Image, width: int) -> Image.Image:
    height = round(width * mark.height / mark.width)
    return mark.resize((width, height), Image.Resampling.LANCZOS)


def centred_mark_layer(mark: Image.Image, size: int, width: int, y_shift: int = 0) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scaled = resize_mark(mark, width)
    x = (size - scaled.width) // 2
    y = (size - scaled.height) // 2 + y_shift
    layer.alpha_composite(scaled, (x, y))
    return layer


def build_app_master(mark: Image.Image, bg: Image.Image) -> Image.Image:
    canvas = bg.resize((APP_SIZE, APP_SIZE), Image.Resampling.LANCZOS).convert("RGBA")
    canvas.alpha_composite(
        centred_mark_layer(mark, APP_SIZE, APP_WORDMARK_WIDTH, APP_OPTICAL_Y)
    )
    canvas.convert("RGB").save(APP_MASTER, "PNG", optimize=True)
    return canvas


def antialiased_shape_mask(size: int, kind: str, radius_ratio: float = 0.22) -> Image.Image:
    ss = 8
    hi = size * ss
    mask = Image.new("L", (hi, hi), 0)
    draw = ImageDraw.Draw(mask)
    if kind == "rounded":
        radius = round(size * radius_ratio * ss)
        draw.rounded_rectangle((0, 0, hi - 1, hi - 1), radius=radius, fill=255)
    elif kind == "circle":
        draw.ellipse((0, 0, hi - 1, hi - 1), fill=255)
    else:
        raise ValueError(kind)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def sharpen_small(image: Image.Image, size: int) -> Image.Image:
    if size > 192:
        return image
    alpha = image.getchannel("A") if image.mode == "RGBA" else None
    rgb = image.convert("RGB").filter(
        ImageFilter.UnsharpMask(radius=0.45, percent=45, threshold=2)
    )
    if alpha is None:
        return rgb
    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    return out


def shaped_icon(master: Image.Image, size: int, shape: str) -> Image.Image:
    image = master.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    image.putalpha(antialiased_shape_mask(size, shape))
    return sharpen_small(image, size)


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
            # to that region prevents faint antialias pixels on the blue letters
            # from being amplified into stray accent specks.
            is_orange = (
                x < mark.width * 0.16
                and y < mark.height * 0.38
                and r > b * 1.15
                and r > g * 1.03
            )
            if is_orange:
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


def write_android(mark: Image.Image, bg: Image.Image, master: Image.Image) -> None:
    drawables = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
    drawables.mkdir(parents=True, exist_ok=True)

    # Adaptive foreground is transparent and centred inside Android's safe zone.
    # The background is a separate layer, so launcher masks cannot crop a square
    # plate or shift the wordmark off-centre.
    foreground = centred_mark_layer(
        mark, ADAPTIVE_SIZE, ADAPTIVE_WORDMARK_WIDTH, ADAPTIVE_OPTICAL_Y
    )
    foreground.save(drawables / "ic_launcher_wordmark.png", "PNG", optimize=True)
    bg.resize((ADAPTIVE_SIZE, ADAPTIVE_SIZE), Image.Resampling.LANCZOS).save(
        drawables / "ic_launcher_background_art.png", "PNG", optimize=True
    )

    for folder, size in LEGACY.items():
        out = ROOT / "app" / "src" / "main" / "res" / folder
        out.mkdir(parents=True, exist_ok=True)
        shaped_icon(master, size, "rounded").save(out / "ic_launcher.png", "PNG", optimize=True)
        shaped_icon(master, size, "circle").save(out / "ic_launcher_round.png", "PNG", optimize=True)


def write_desktop(master: Image.Image) -> None:
    png = ROOT / "desktop" / "src" / "main" / "resources" / "icon.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    shaped_icon(master, 512, "rounded").save(png, "PNG", optimize=True)

    ico = ROOT / "desktop" / "icon.ico"
    base = shaped_icon(master, 256, "rounded")
    base.save(ico, "ICO", sizes=[(s, s) for s in ICO_SIZES])

    # Repository/documentation icon gets the same transparent rounded plate.
    shaped_icon(master, 1024, "rounded").save(ROOT / "docs" / "icon.png", "PNG", optimize=True)


def main() -> None:
    mark, bg = load_masters()
    master = build_app_master(mark, bg)
    write_dynamic_wordmark_masks(mark)
    write_android(mark, bg, master)
    write_desktop(master)
    print(f"wordmark master: {mark.width}x{mark.height} ({mark.width / mark.height:.6f})")
    print(f"app icon master: {APP_SIZE}x{APP_SIZE}; centred wordmark width {APP_WORDMARK_WIDTH}")
    print("wrote Android adaptive + legacy icons, dynamic wordmark masks, and rounded desktop icons")


if __name__ == "__main__":
    main()
