#!/usr/bin/env python3
"""Offline source contracts; not a Kotlin compiler or a screenshot test."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / 'shared/src/jvmShared/kotlin/dev/ikna'
ANDROID = ROOT / 'app/src/main/java/dev/ikna'
DESKTOP = ROOT / 'desktop/src/main/kotlin/dev/ikna/desktop'


def read(base, name):
    return (base / name).read_text(encoding='utf-8')


class DesignContracts(unittest.TestCase):
    def test_windows_title_bar_is_themed_and_other_frames_stay_native(self):
        main = read(DESKTOP, 'Main.kt')
        bar = read(DESKTOP, 'WindowTitleBar.kt')
        shell = read(DESKTOP, 'Shell.kt')
        for text in ['undecorated = customTitleBar', 'resizable = !customTitleBar',
                     'windowState.placement != WindowPlacement.Fullscreen',
                     'IknaWindowTitleBar(windowState, palette, closeWindow)',
                     'onCloseRequest = closeWindow', 'saveGeometry(home, windowState)']:
            self.assertIn(text, main)
        for text in ['WindowDraggableArea(', 'state.isMinimized = true',
                     'WindowPlacement.Maximized', 'WindowPlacement.Floating',
                     'PointerEventPass.Initial', 'LocalWindowInfo', 'Role.Button',
                     'IknaWordmark(', 'palette.background', 'collectIsFocusedAsState']:
            self.assertIn(text, bar)
        self.assertNotIn('.consume(', bar)
        self.assertIn('titleBar(palette)', shell)
        self.assertLess(shell.index('IknaTheme('), shell.index('titleBar(palette)'))
        self.assertLess(main.index('runBlocking {\n        runCatching { container.install()'),
                        main.index('    application {'))

    def test_window_controls_are_named_in_all_six_locales(self):
        for language in ['En', 'Ru', 'Pl', 'De', 'Es', 'Fr']:
            source = read(SHARED, 'ui/text/Strings' + language + '.kt')
            for number in range(17, 21):
                self.assertIn('"pc.%03d" to ' % number, source)

    def test_model_controls_are_not_exposed(self):
        for base, name in [(ANDROID, 'ui/settings/SettingsScreen.kt'), (DESKTOP, 'SettingsPane.kt')]:
            source = read(base, name)
            for forbidden in ['LocalOptimizerPanel', 'grading.001', 'grading.002', 'setDerivedGrading', 'applyCandidate', 'setEnabled']:
                self.assertNotIn(forbidden, source)
        self.assertFalse((SHARED / 'ui/settings/LocalOptimizerPanel.kt').exists())

    def test_automatic_policy_starts_in_both_containers(self):
        for base, name in [(ANDROID, 'AppContainer.kt'), (DESKTOP, 'DesktopContainer.kt')]:
            source = read(base, name)
            for required in ['AutomaticLearningPolicy.DERIVED_WHEN_READY', 'optimizer.initialize()', 'optimizer.startAutomatic', 'observeOptimizerChanges()']:
                self.assertIn(required, source)
            self.assertNotIn('derivedGradingEnabled = { settings.current()', source)

    def test_automatic_worker_has_quiet_period_and_backoff(self):
        source = read(SHARED, 'data/repo/LocalOptimizer.kt')
        for required in ['Channel.CONFLATED', 'withTimeoutOrNull(20_000L)', 'delay(3_600_000L)', 'automaticRetryNotBefore', 'job.join()', 'fresh != before.latest', 'applyCandidateAt(generation)']:
            self.assertIn(required, source)
        self.assertIn('86_400_000L', read(SHARED, 'domain/optimizer/AutomaticLearningPolicy.kt'))

    def test_generation_and_fingerprint_guards_remain(self):
        source = read(SHARED, 'data/repo/LocalOptimizer.kt')
        self.assertIn('candidate.sourceFingerprint == input().fingerprint', source)
        self.assertIn('job.isCancelled || generation != epoch.get()', source)
        self.assertIn('epoch.incrementAndGet(); active.set(defaults)', source)
        self.assertIn('source.fingerprint != input().fingerprint', source)

    def test_same_stats_renderer_and_empty_states(self):
        for base, name in [(ANDROID, 'ui/stats/StatsScreen.kt'), (DESKTOP, 'StatsPane.kt')]:
            self.assertIn('IknaStatsContent(', read(base, name))
        source = read(SHARED, 'ui/stats/StatsContent.kt')
        for required in ['ActivityMap(', 'HourBars(', 'ForecastBars(', 'Leeches(', 'stats.030', 'displayLarge']:
            self.assertIn(required, source)

    def test_settings_navigation_and_section_order(self):
        mobile = read(ANDROID, 'ui/settings/SettingsScreen.kt')
        desktop = read(DESKTOP, 'SettingsPane.kt')
        for source in [mobile, desktop]:
            self.assertIn('IknaSettingsJumpRow(', source)
            self.assertIn('LazyColumn(', source)
            self.assertIn('IknaSettingsSection(', source)
        actual = re.findall(r'item\(key = (ID_\w+), contentType', mobile)
        declared = re.findall(r'^    (ID_\w+) to "set\.', mobile, re.M)
        self.assertEqual(actual, declared)
        self.assertEqual(len(actual), 9)
        self.assertLess(desktop.index('item(key = "update"'), desktop.index('item(key = "data"'))
        self.assertIn('verticalScrolling', read(SHARED, 'ui/settings/SettingsChrome.kt'))

    def test_deck_marks_and_phonetics_are_real(self):
        for base, name in [(ANDROID, 'ui/decks/DeckScreen.kt'), (DESKTOP, 'DeckPane.kt')]:
            source = read(base, name)
            for required in ['IknaDeckAppearance(', 'Phonetics.sample(', 'current.hasPhonetics', 'LangChips(']:
                self.assertIn(required, source)
        self.assertIn('FlowRow(', read(SHARED, 'ui/decks/DeckLangs.kt'))

    def test_session_uses_shared_quiet_chrome(self):
        for base, name in [(ANDROID, 'ui/session/SessionScreen.kt'), (DESKTOP, 'SessionPane.kt')]:
            source = read(base, name)
            for required in ['IknaSessionTopBar(', 'IknaSessionEmptyState(', 'IknaSessionUndoBar(', 'catalogCardReport', 'tatoebaSentenceUrl', 'IknaDialog(']:
                self.assertIn(required, source)
        source = read(DESKTOP, 'SessionPane.kt')
        self.assertIn('if (result.isSuccess)', source)
        self.assertIn('!loading && !saving', source)
        self.assertIn('Key.Z', source)
        self.assertIn('cardWidthPx * 0.13f', source)

    def test_source_aware_search_and_catalog_are_shared(self):
        for base, name in [(ANDROID, 'ui/search/DeckSearchScreen.kt'), (DESKTOP, 'SearchPane.kt')]:
            self.assertIn('IknaSearchResult(', read(base, name))
        for base, name in [(ANDROID, 'ui/catalog/CatalogScreen.kt'), (DESKTOP, 'CatalogPane.kt')]:
            self.assertIn('IknaCatalogDeckRow(', read(base, name))
        self.assertIn('if (token != request)', read(DESKTOP, 'SearchPane.kt'))
        self.assertIn('request == previewToken', read(DESKTOP, 'CatalogPane.kt'))

    def test_model_path_and_paste_cost_are_bounded(self):
        source = read(DESKTOP, 'AddDeckPane.kt')
        for required in ['if (modelWay)', 'ClasspathAssets.open', 'fillSubjectPrompt(', 'fillPrompt(', 'lineSequence().take(40)', '1_600_000', 'clipboard.getText()', 'lang = if (subject) NO_LANG else lang']:
            self.assertIn(required, source)
        self.assertIn('firstProblem', read(SHARED, 'ui/decks/ImportDescription.kt'))
        self.assertIn('firstWarning', read(SHARED, 'ui/decks/ImportDescription.kt'))
        self.assertIn('showConfirmDialog', source)

    def test_desktop_geometry_is_not_a_phone_window(self):
        source = read(DESKTOP, 'Shell.kt')
        for required in ['maxWidth >= 900.dp', '380.dp', '340.dp', '300.dp', 'deckListState', '.clipToBounds()']:
            self.assertIn(required, source)
        for name in ['StatsPane', 'SettingsPane', 'DeckPane', 'SearchPane', 'CatalogPane', 'AddDeckPane', 'AnkiPane', 'BackupPane']:
            self.assertRegex(read(DESKTOP, name + '.kt'), r'Desktop(?:ScrollablePane|PaneFrame)\(')
        self.assertIn('readingWidth: Dp = 760.dp', read(DESKTOP, 'PaneChrome.kt'))
        self.assertIn('IknaBottomBar', read(DESKTOP, 'PaneChrome.kt'))

    def test_button_appearance_has_one_implementation(self):
        source = read(DESKTOP, 'Widgets.kt')
        button = source[source.index('fun IknaButton('):source.index('fun RailButton(')]
        self.assertIn('IknaWideButton(', button)
        self.assertIn('height = 38.dp, fillWidth = false', button)
        self.assertNotIn('.border(', button)
        self.assertIn('fillWidth: Boolean = true', read(SHARED, 'ui/theme/Flat.kt'))

    def test_shared_ui_has_no_android_or_desktop_host_dependency(self):
        names = ['stats/StatsContent.kt', 'settings/SettingsChrome.kt', 'catalog/CatalogRows.kt', 'search/SearchResult.kt', 'session/SessionChrome.kt', 'session/SessionUiState.kt', 'decks/DeckAppearance.kt', 'decks/DeckLangs.kt', 'decks/DeckPrompt.kt', 'decks/PromptControls.kt', 'decks/ImportDescription.kt']
        for name in names:
            source = read(SHARED, 'ui/' + name)
            self.assertNotRegex(source, r'^import (?:android\.|dev\.ikna\.(?:desktop\.|AppContainer))', name)
            self.assertNotIn('LocalContext', source)

    def test_all_six_locales_have_overwrite_confirmation(self):
        for language in ['En', 'Ru', 'Pl', 'De', 'Es', 'Fr']:
            self.assertIn('"file.001" to ', read(SHARED, 'ui/text/Strings' + language + '.kt'))

    def test_compose_typography_references_have_imports(self):
        # Imported Android UI bodies can keep FontWeight.Medium while losing
        # their file-level imports. Check all three production source roots;
        # this guard supplements, and does not replace, the Kotlin compiler.
        symbols = {
            'FontWeight': 'androidx.compose.ui.text.font.FontWeight',
            'FontStyle': 'androidx.compose.ui.text.font.FontStyle',
            'TextAlign': 'androidx.compose.ui.text.style.TextAlign',
            'TextOverflow': 'androidx.compose.ui.text.style.TextOverflow',
            'TextDecoration': 'androidx.compose.ui.text.style.TextDecoration',
        }
        for base in [ANDROID, DESKTOP, SHARED]:
            for path in sorted(base.rglob('*.kt')):
                source = path.read_text(encoding='utf-8')
                imports = set(re.findall(r'^import ([\w.*]+)\s*$', source, re.M))
                body = re.sub(r'^import .*$|^package .*$', '', source, flags=re.M)
                body = re.sub(r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"',
                              '', body, flags=re.S)
                for symbol, qualified in symbols.items():
                    if not re.search(r'(?<![\w.])' + symbol + r'\b', body):
                        continue
                    package_wildcard = qualified.rsplit('.', 1)[0] + '.*'
                    with self.subTest(path=path.relative_to(ROOT), symbol=symbol):
                        self.assertTrue(qualified in imports or package_wildcard in imports,
                                        f'{path.relative_to(ROOT)} uses {symbol} without importing {qualified}')

    def test_junit_settings_contracts_use_the_same_executable_preflight(self):
        helper_path = 'app/src/test/java/dev/ikna/ui/SettingsSourceContracts.java'
        helper = read(ROOT, helper_path)
        cases = {
            'settings/SettingsLazyLayoutTest.kt': [
                'offscreenSettingsAreNotComposedEagerly',
                'jumpStripTargetsLazyItemsWithoutGlobalSectionMeasurement',
                'speechEngineWaitsUntilItsSectionIsVisible'],
            'theme/MotionPolishTest.kt': [
                'fastSettingsFlingDoesNotStartACompetingJumpAnimation',
                'autoLoadTargetIsPublishedOnlyAfterMeasurementIsKnown',
                'microMotionIsShortLocalAndObeysTheExistingSwitch'],
        }
        for name, methods in cases.items():
            wrapper = read(ROOT, 'app/src/test/java/dev/ikna/ui/' + name)
            self.assertEqual(wrapper.count('@Test'), 3)
            self.assertNotIn('@Ignore', wrapper)
            for method in methods:
                self.assertIn('contracts.' + method + '()', wrapper)
                self.assertIn('public void ' + method + '()', helper)
        self.assertIn('ui/settings/SettingsChrome.kt', helper)
        self.assertIn('throw new AssertionError', helper)
        ci = read(ROOT, '.github/workflows/grading.yml')
        self.assertIn('javac --release 8 -encoding UTF-8', ci)
        self.assertIn(helper_path, ci)
        self.assertIn('java -cp "$classes" dev.ikna.ui.SettingsSourceContracts', ci)
        self.assertIn('new String(Files.readAllBytes(', helper)
        for incompatible in ['Files.readString(', '.results()', 'android.lines()']:
            self.assertNotIn(incompatible, helper)
        self.assertIn('jvm-build.log --continue :desktop:test :app:testReleaseUnitTest :app:assembleDebug', ci)

    def test_ci_keeps_real_build_and_migration_gates(self):
        source = read(ROOT, '.github/workflows/grading.yml')
        for required in ['tools/check_design_parity.py', ':desktop:test', ':app:testReleaseUnitTest', ':app:assembleDebugAndroidTest', ':app:connectedDebugAndroidTest']:
            self.assertIn(required, source)
        source = read(ROOT, 'app/src/test/java/dev/ikna/domain/optimizer/LocalOptimizerTest.kt')
        for test in ['automaticPolicyAppliesOnlyAcceptedResultsAndHonoursMonthlyLimit', 'automaticEligibilityIsQuietAndInsufficientHistoryKeepsDefaults', 'automaticActivationCannotReviveAProfileAfterReset', 'automaticFailuresBackOffInsteadOfFittingAfterEveryAnswer']:
            self.assertIn(test, source)


if __name__ == '__main__':
    unittest.main(verbosity=2)
