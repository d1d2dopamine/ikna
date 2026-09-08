#!/usr/bin/env python3
"""Generate a colour study from current source values, not an application screenshot.

Standard library only. Run from any directory; --check verifies committed output.
"""
from pathlib import Path
from html import escape
import sys
from check_palettes import palettes, control_colors, hex_of, contrast, luminance

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/palettes-preview.svg'


def render():
    width, row_height = 1400, 194
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{80 + 12 * row_height}" viewBox="0 0 {width} {80 + 12 * row_height}">',
             '<title>Ikna: grey light lighting and quiet themed controls</title>',
             '<desc>Colour study of all 24 authored combinations, generated from production source values. Not an application screenshot.</desc>',
             '<rect width="100%" height="100%" fill="#26292D"/>']

    def rect(x, y, w, h, fill, stroke=None, weight=1):
        # Filled nested rectangles match Compose's inside border and render
        # consistently even in lightweight SVG converters that omit strokes.
        if stroke is not None:
            parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#{hex_of(stroke)}"/>')
            x, y, w, h = x + weight, y + weight, w - 2 * weight, h - 2 * weight
        parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#{hex_of(fill)}"/>')

    def text(x, y, label, color, size=14):
        parts.append(f'<text x="{x}" y="{y}" fill="#{hex_of(color)}" font-family="Arial, sans-serif" font-size="{size}">{escape(label)}</text>')

    from check_palettes import rgb
    text(20, 28, 'IKNA / QUIET CONTROLS / GREY LIGHT LIGHTING', rgb('CFD2D6'), 20)
    text(20, 55, 'Source-derived colour study. Dark left / Light right. Not an application screenshot.', rgb('B0B5BD'), 14)
    data = palettes()
    for index, ((identity, mode), p) in enumerate(data.items()):
        c = control_colors(p)
        x = 10 + (index % 2) * 695
        y = 76 + (index // 2) * row_height
        rect(x, y, 685, 184, p['background'])
        text(x + 18, y + 27, identity.upper() + ' / ' + mode.upper(), p['ink'], 19)
        text(x + 18, y + 49, 'background #' + hex_of(p['background']) + ' / accent #' + hex_of(p['accent']), p['muted'], 12)
        text(x + 18, y + 72, 'Readable text. Calm surfaces, not inverted white rectangles.', p['ink'], 15)
        # Filled, outlined, quiet, selected and switch states use the same roles
        # as Flat.kt. Proportions here are a compact colour swatch, not app layout.
        for offset, label, fill, border in [(18, 'PRIMARY', c['fill'], c['mark']),
                                            (154, 'SECONDARY', p['background'], c['outline']),
                                            (298, 'SELECTED', c['fill'], c['mark'])]:
            rect(x + offset, y + 89, 122, 37, fill, border, 2 if label == 'SELECTED' else 1)
            text(x + offset + 10, y + 112, label, c['label'], 12)
        for dx, on in [(438, False), (530, True)]:
            rect(x + dx, y + 91, 56, 32, c['fill'] if on else c['idle'], c['mark'] if on else c['outline'])
            rect(x + dx + (28 if on else 4), y + 95, 24, 24,
                 c['mark'] if on else c['idle'], c['mark'] if on else c['outline'])
            text(x + dx + 62, y + 112, 'ON' if on else 'OFF', p['muted'], 11)
        text(x + 18, y + 151, 'Quiet action', c['quietLabel'])
        rect(x + 160, y + 137, 106, 29, c['hover'], c['outline'])
        text(x + 172, y + 157, 'HOVER', c['label'], 11)
        rect(x + 279, y + 137, 106, 29, c['pressed'], c['mark'])
        text(x + 291, y + 157, 'PRESSED', c['label'], 11)
        min_text = min(contrast(c['quietLabel'], c[name]) for name in ['idle', 'fill', 'hover', 'pressed'])
        text(x + 410, y + 151, f'quiet text {min_text:.2f}:1', p['muted'], 12)
        text(x + 410, y + 168, f'background luminance {luminance(p["background"]):.3f}', p['muted'], 12)
    parts.append('</svg>')
    return '\n'.join(parts) + '\n'


if __name__ == '__main__':
    content = render()
    if '--check' in sys.argv:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding='utf-8') != content:
            raise SystemExit('Preview is stale; run python3 tools/make_palette_preview.py')
        print('Palette preview agrees with all 24 production palette combinations.')
    else:
        OUTPUT.write_text(content, encoding='utf-8')
        print(OUTPUT.relative_to(ROOT))
