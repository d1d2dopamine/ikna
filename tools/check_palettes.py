#!/usr/bin/env python3
"""Offline colour arithmetic + source contracts. Not a Kotlin compiler or UI test.

Reads all authored hexes and mixing constants from production sources. sRGB
mixing rounds to 8-bit channels just like Compose's sRGB Color constructor.
Run the Kotlin ControlColorsTest in CI as the independent production-code check.
"""
from pathlib import Path
import math
import re
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
THEME = ROOT / 'shared/src/jvmShared/kotlin/dev/ikna/ui/theme'


def f32(v):
    return struct.unpack('f', struct.pack('f', v))[0]


def rgb(hex_value):
    return tuple(f32(int(hex_value[i:i + 2], 16) / 255) for i in (0, 2, 4))


def hex_of(c):
    return ''.join(f'{math.floor(v * 255 + .5):02X}' for v in c)


def blend(a, b, t):
    return tuple(f32(math.floor(f32(f32(x + f32(f32(y - x) * f32(t))) * 255) + .5) / 255)
                 for x, y in zip(a, b))


def luminance(c):
    return sum(w * (v / 12.92 if v <= .03928 else ((v + .055) / 1.055) ** 2.4)
               for w, v in zip((.2126, .7152, .0722), c))


def contrast(a, b):
    x, y = luminance(a), luminance(b)
    return (max(x, y) + .05) / (min(x, y) + .05)


def palettes():
    source = (THEME / 'Theme.kt').read_text(encoding='utf-8')
    result = {}
    for piece in source[source.index('val IknaPalettes:'):source.index('/** The palette behind a stored id.')].split('    IknaPaletteSpec(')[1:]:
        identity = re.search(r'id = "([a-z]+)"', piece).group(1)
        values = re.findall(r'(background|ink|muted|accent) = Color\(0xFF([0-9A-F]{6})\)', piece)
        if len(values) != 8:
            raise AssertionError(f'{identity}: expected eight literal base colours, got {len(values)}')
        for lighting, start in [('dark', 0), ('light', 4)]:
            result[(identity, lighting)] = {key: rgb(value) for key, value in values[start:start + 4]}
    if len(result) != 24:
        raise AssertionError(f'Expected 24 palette/light combinations, got {len(result)}')
    return result


def constants():
    source = (THEME / 'ControlColors.kt').read_text(encoding='utf-8')
    return {key: float(value) for key, value in re.findall(r'const val (\w+) = ([0-9.]+)f?', source)}


def control_colors(p):
    cfg = constants()
    bg, ink, accent = (p[key] for key in ['background', 'ink', 'accent'])
    light = luminance(bg) > .45

    def fill(tint, amount):
        desired = blend(bg, tint, amount)
        if contrast(ink, desired) >= 4.5:
            return desired
        low, high = 0., f32(amount)
        for _ in range(20):
            mid = f32(f32(low + high) / 2)
            if contrast(ink, blend(bg, tint, mid)) >= 4.5:
                low = mid
            else:
                high = mid
        return blend(bg, tint, low)

    mode = 'LIGHT' if light else 'DARK'
    result = {key: fill(accent, cfg['CONTROL_' + role + '_' + mode])
              for key, role in [('fill', 'FILL'), ('hover', 'HOVER'), ('pressed', 'PRESS')]}
    result['idle'] = fill(ink, cfg['CONTROL_IDLE_MIX'])
    surfaces = [bg, *result.values()]

    def mark(preferred, minimum):
        def clears(c):
            return all(contrast(c, s) >= minimum for s in surfaces)
        target = preferred if clears(preferred) else ink if clears(ink) else max(
            [ink, rgb('000000'), rgb('FFFFFF')], key=lambda c: min(contrast(c, s) for s in surfaces))
        if not clears(target):
            return target
        low, high = 0., 1.
        for _ in range(20):
            mid = f32(f32(low + high) / 2)
            if clears(blend(bg, target, mid)):
                high = mid
            else:
                low = mid
        return blend(bg, target, high)

    result['label'] = ink
    result['quietLabel'] = mark(p['muted'], cfg['QUIET_TEXT_CONTRAST'])
    result['outline'] = mark(p['muted'], cfg['MARK_CONTRAST'])
    result['mark'] = mark(accent, cfg['MARK_CONTRAST'])
    return result


class PaletteContracts(unittest.TestCase):
    def test_light_lighting_is_grey_in_all_twelve_palettes(self):
        for (identity, mode), p in palettes().items():
            with self.subTest(palette=identity, mode=mode):
                value = luminance(p['background'])
                if mode == 'light':
                    self.assertTrue(.55 <= value <= .70)
                    self.assertLess(max(p['background']) - min(p['background']), .09)
                else:
                    self.assertLess(value, .05)

    def test_authored_text_and_light_panels_remain_readable(self):
        for (identity, mode), p in palettes().items():
            for key in ['ink', 'muted', 'accent']:
                with self.subTest(palette=identity, mode=mode, role=key):
                    self.assertGreaterEqual(contrast(p[key], p['background']), 4.5)
                    if mode == 'light':
                        self.assertGreaterEqual(contrast(p[key], blend(p['background'], p['ink'], .07)), 4.5)

    def test_danger_is_readable_and_warm_accents_keep_ink(self):
        import colorsys
        for key, p in palettes().items():
            h, sat, _ = colorsys.rgb_to_hsv(*p['accent'])
            distance = abs(h * 360 - 8)
            warm = sat >= .35 and min(distance, 360 - distance) < 26
            danger = p['ink'] if warm else rgb('962A17' if key[1] == 'light' else 'FF7A66')
            self.assertGreaterEqual(contrast(danger, p['background']), 4.5, key)
        for mode in ['dark', 'light']:
            h, s, _ = colorsys.rgb_to_hsv(*palettes()[('ultraviolet', mode)]['accent'])
            self.assertTrue(250 <= h * 360 <= 290 and s > .35)

    def test_every_enabled_label_survives_all_control_states(self):
        for key, p in palettes().items():
            c = control_colors(p)
            for surface in [p['background'], *(c[name] for name in ['idle', 'fill', 'hover', 'pressed'])]:
                for label in ['label', 'quietLabel']:
                    self.assertGreaterEqual(contrast(c[label], surface), 4.5, (key, label))

    def test_boundaries_and_thumbs_are_distinct_without_white_fills(self):
        for (identity, mode), p in palettes().items():
            c = control_colors(p)
            for name in ['idle', 'fill', 'hover', 'pressed']:
                surface = c[name]
                self.assertLessEqual(contrast(surface, p['background']), 1.8, (identity, mode, name))
                for mark in ['outline', 'mark']:
                    self.assertGreaterEqual(contrast(c[mark], surface), 3.0)
                if mode == 'dark':
                    self.assertLess(luminance(surface), .06)
                    self.assertLess(luminance(c['mark']), .25)
                else:
                    self.assertLessEqual(luminance(surface), .70)
            self.assertNotEqual(c['fill'], p['ink'])

    def test_valid_custom_pairs_keep_their_original_label_and_readability(self):
        for background, ink, accent in [
            ('102030', 'F0F0F0', '40A0FF'), ('D2D2D2', '242424', '343434'),
            ('FFFFFF', '767676', 'FF0000'), ('000000', '757575', 'FFFFFF'),
            ('888888', '111111', '222222'), ('FFEEEE', '501020', '772244')]:
            p = dict(zip(['background', 'ink', 'accent', 'muted'], map(rgb, [background, ink, accent, ink])))
            original = p.copy()
            self.assertGreaterEqual(contrast(p['ink'], p['background']), 4.5)
            c = control_colors(p)
            self.assertEqual(p, original)
            self.assertEqual(c['label'], p['ink'])
            for name in ['fill', 'hover', 'pressed', 'idle']:
                self.assertGreaterEqual(contrast(c['label'], c[name]), 4.5)

    def test_shared_controls_no_longer_invert_the_text_ink(self):
        flat = (THEME / 'Flat.kt').read_text(encoding='utf-8')
        for begin, end in [('fun IknaWideButton(', 'fun IknaTextButton('),
                           ('fun IknaToggle(', 'fun IknaChip('),
                           ('fun IknaChip(', 'fun IknaHexField(')]:
            block = flat[flat.index(begin):flat.index(end)]
            self.assertIn('LocalIknaControlColors.current', block)
            self.assertIn('collectIsFocusedAsState()', block)
            self.assertIn('indication = null', block)
            self.assertNotIn('else ink', block)
            self.assertNotIn('Color.White', block)
            self.assertNotIn('if (filled) paper', block)
        self.assertIn('targetValue = if (checked) 24.dp else 0.dp', flat)
        self.assertIn('role = Role.Switch', flat)
        self.assertIn('.semantics { this.selected = selected }', flat)
        self.assertIn('else snap()', flat)
        self.assertEqual(flat.count('by key(colors) { animateColorAsState('), 5)
        self.assertEqual(flat.count('else -> colors.fill.copy(alpha = 0f)'), 2)

    def test_one_provider_drives_both_platforms_and_windows_bar(self):
        theme = (THEME / 'Theme.kt').read_text(encoding='utf-8')
        self.assertIn('val controls = remember(palette) { controlColors(palette) }', theme)
        self.assertIn('LocalIknaControlColors provides controls', theme)
        self.assertEqual(theme.count('primaryContainer = controls.fill'), 2)
        self.assertIn('ThemeMode.LIGHT -> spec.light', theme)
        self.assertIn('ThemeMode.CUSTOM -> customPaletteOf(settings)', theme)
        bar = (ROOT / 'desktop/src/main/kotlin/dev/ikna/desktop/WindowTitleBar.kt').read_text(encoding='utf-8')
        self.assertIn('LocalIknaControlColors.current', bar)
        self.assertNotIn('tint.copy(alpha', bar)
        desktop = (ROOT / 'desktop/src/main/kotlin/dev/ikna/desktop/Widgets.kt').read_text(encoding='utf-8')
        self.assertIn('IknaWideButton(label, onClick', desktop)


if __name__ == '__main__':
    unittest.main(verbosity=2)
