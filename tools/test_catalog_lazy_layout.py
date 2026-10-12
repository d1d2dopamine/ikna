#!/usr/bin/env python3
"""Offline catalogue lazy-layout contracts, not compilation or runtime/layout evidence."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / 'app/src/main/java/dev/ikna/ui/catalog/CatalogScreen.kt'
DESKTOP = ROOT / 'desktop/src/main/kotlin/dev/ikna/desktop/CatalogPane.kt'
CHROME = ROOT / 'desktop/src/main/kotlin/dev/ikna/desktop/PaneChrome.kt'


class CatalogLazyLayoutContracts(unittest.TestCase):
    def test_rows_are_lazy_and_keyed_by_identity_not_position(self):
        for path in (ANDROID, DESKTOP):
            with self.subTest(platform=path.name):
                source = path.read_text(encoding='utf-8')
                self.assertIn('import androidx.compose.foundation.lazy.items', source)
                self.assertIn('items(decks, key = { "deck:${it.id}" }, contentType = { "deck" })', source)
                self.assertNotIn('decks.forEach', source)
                self.assertNotIn('verticalScroll(', source)
                for key in ('intro', 'filters', 'loading', 'error', 'empty', 'footer', 'bottom-space'):
                    self.assertIn('item(key = "catalog-' + key + '")', source)

    def test_viewports_are_bounded_and_desktop_retains_reading_measure(self):
        android = ANDROID.read_text(encoding='utf-8')
        self.assertEqual(android.count('LazyColumn('), 1)
        viewport = android.split('LazyColumn(', 1)[1].split('item(key', 1)[0]
        self.assertIn('.weight(1f)', viewport)
        self.assertIn('.padding(horizontal = Edge)', viewport)
        desktop = DESKTOP.read_text(encoding='utf-8')
        self.assertIn('DesktopLazyPane(S.t("cat.001"), onBack)', desktop)
        self.assertNotIn('DesktopScrollablePane(', desktop)
        chrome = CHROME.read_text(encoding='utf-8').split('fun DesktopLazyPane(', 1)[1]
        self.assertEqual(chrome.count('LazyColumn('), 1)
        self.assertIn('DesktopPaneFrame(title, onBack, titleStyle)', chrome)
        self.assertIn('readingWidth: Dp = 760.dp', chrome)
        self.assertIn('Box(Modifier.fillMaxSize().padding(horizontal = 40.dp))', chrome)
        self.assertIn('Modifier.widthIn(max = readingWidth).fillMaxWidth().fillMaxHeight()', chrome)
        self.assertIn('.align(Alignment.TopCenter)', chrome)
        self.assertIn('PaddingValues(top = Space.md, bottom = Space.xxl)', chrome)
        self.assertNotIn('verticalScroll(', chrome)

    def test_work_and_result_state_outlive_row_composition(self):
        for path in (ANDROID, DESKTOP):
            with self.subTest(platform=path.name):
                source = path.read_text(encoding='utf-8')
                rows = source.index('items(decks,')
                self.assertEqual(source.count('rememberCoroutineScope()'), 1)
                self.assertLess(source.index('rememberCoroutineScope()'), rows)
                self.assertLess(source.index('val notes = remember'), rows)
                self.assertLess(source.index('val planRefreshFailures = remember'), rows)
                self.assertLess(source.index('LaunchedEffect(Unit)'), rows)
                self.assertNotIn('LaunchedEffect(', source[rows:])
                self.assertNotIn('rememberCoroutineScope()', source[rows:])
                for binding in ('resultNote = notes[', 'planRefreshFailed = planRefreshFailures[',
                                'onRetryPlan =', 'previewCards =', 'onInstall ='):
                    self.assertIn(binding, source[rows:])

    def test_filter_and_atomic_import_paths_remain_in_use(self):
        for path in (ANDROID, DESKTOP):
            with self.subTest(platform=path.name):
                source = path.read_text(encoding='utf-8')
                for api in ('normalizeCatalogFilter(', 'decksFor(', 'learnableLangs(',
                            'meaningLangsFor(', 'subjectsFor(', 'levelsFor(', 'tierOf(',
                            'container.importCatalogDeck(', 'currentCoroutineContext().ensureActive()'):
                    self.assertIn(api, source)
                self.assertNotIn('packLoader.', source)


if __name__ == '__main__':
    unittest.main(verbosity=2)
