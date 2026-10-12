#!/usr/bin/env python3
"""Source guardrails only; does not simulate Compose layout or pointer input."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / 'shared/src/jvmShared/kotlin/dev/ikna/ui'


class SmallViewportContract(unittest.TestCase):
    def test_bounded_scroll_preserves_weighted_spacing(self):
        source = (SHARED / 'theme/ScrollableColumn.kt').read_text()
        self.assertIn('BoxWithConstraints(modifier)', source)
        self.assertIn('.verticalScroll(scroll)\n                .heightIn(min = maxHeight)\n                .height(IntrinsicSize.Min)', source)
        self.assertIn('remember(scrollKey) { ScrollState(0) }', source)

    def test_both_introductions_scroll_and_reset_on_slide_change(self):
        for path in [
            'app/src/main/java/dev/ikna/ui/onboarding/OnboardingScreen.kt',
            'desktop/src/main/kotlin/dev/ikna/desktop/OnboardingPane.kt',
        ]:
            source = (ROOT / path).read_text()
            with self.subTest(path=path):
                self.assertIn('IknaScrollableColumn(', source)
                self.assertIn('scrollKey = step', source)
                self.assertIn('.heightIn(min = 132.dp)', source)
                self.assertEqual(source.count('.fillMaxWidth(0.5f)'), 2)
                self.assertIn('branded = step == 0', source)

    def test_card_vertical_scroll_does_not_claim_rating_axis(self):
        source = (SHARED / 'session/CardStack.kt').read_text()
        self.assertIn('IknaScrollableColumn(', source)
        self.assertIn('scrollKey = prompt', source)
        self.assertIn('detectHorizontalDragGestures(', source)
        self.assertNotIn('detectDragGestures(', source)
        self.assertIn('val next = drag.value + delta', source)
        self.assertIn('decideRating(drag.value, 0f, velocity.x, line)', source)
        for name in ['SessionChrome.kt', 'CardLoadFailure.kt']:
            self.assertIn('IknaScrollableColumn(', (SHARED / 'session' / name).read_text())

    def test_targets_grow_without_enlarging_toggle_artwork(self):
        source = (SHARED / 'theme/Flat.kt').read_text()
        self.assertIn('.size(size.coerceAtLeast(48.dp))', source)
        self.assertIn('.sizeIn(minWidth = 48.dp, minHeight = 48.dp)', source)
        self.assertIn('.heightIn(min = height.coerceAtLeast(48.dp))', source)
        toggle = source.split('fun IknaToggle(', 1)[1].split('fun IknaChip(', 1)[0]
        self.assertIn('.height(48.dp)', toggle)
        self.assertLess(toggle.index('.toggleable('), toggle.index('.padding(vertical = 8.dp)'))
        self.assertLess(toggle.index('.padding(vertical = 8.dp)'), toggle.index('.background(trackColor'))
        self.assertIn('val knobSize = if (roundedKnob) 22.dp else 24.dp', toggle)
        rows = (SHARED / 'decks/DeckList.kt').read_text()
        self.assertEqual(rows.count('size = 48.dp,'), 2)
        self.assertIn('.heightIn(min = DECK_ROW_HEIGHT)', rows)
        self.assertNotIn('.height(DECK_INFO_HEIGHT)', rows)
        self.assertIn('private val DECK_MARK_SIZE = 52.dp', rows)


if __name__ == '__main__':
    unittest.main()
