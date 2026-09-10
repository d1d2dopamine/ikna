#!/usr/bin/env python3
"""Offline source contracts; not a Kotlin compiler or a screenshot test."""
from pathlib import Path
import json
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / 'shared/src/jvmShared/kotlin/dev/ikna'
ANDROID = ROOT / 'app/src/main/java/dev/ikna'
DESKTOP = ROOT / 'desktop/src/main/kotlin/dev/ikna/desktop'


def read(base, name):
    return (base / name).read_text(encoding='utf-8')


def interface_locale_suffixes():
    text_dir = SHARED / 'ui/text'
    return sorted(path.stem.removeprefix('Strings') for path in text_dir.glob('Strings??.kt'))


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

    def test_window_controls_are_named_in_all_locales(self):
        for language in interface_locale_suffixes():
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

    def test_all_locales_have_overwrite_confirmation(self):
        for language in interface_locale_suffixes():
            self.assertIn('"file.001" to ', read(SHARED, 'ui/text/Strings' + language + '.kt'))

    def test_interface_language_registry_drives_both_targets(self):
        registry = read(SHARED, 'ui/text/UiLanguages.kt')
        self.assertEqual(registry.count('UiLanguage(LANG_'), 7)
        for required in ['LANG_PT, "PORTUGUÊS (BRASIL)"', 'STRINGS_PT',
                         'QuantityRule.SLAVIC', 'QuantityRule.ONE_OTHER']:
            self.assertIn(required, registry)
        strings = read(SHARED, 'ui/text/Strings.kt')
        self.assertIn('uiLanguage(raw)?.code ?: LANG_EN', strings)
        self.assertIn('uiLanguage(lang)?.strings ?: STRINGS_EN', strings)
        quantity = read(SHARED, 'ui/text/QuantityText.kt')
        self.assertIn('uiLanguage(S.lang)?.quantityRule', quantity)
        for base, name in [(ANDROID, 'ui/settings/SettingsScreen.kt'), (DESKTOP, 'SettingsPane.kt')]:
            source = read(base, name)
            self.assertIn('UI_LANGUAGES.map { it.code }', source)
            self.assertIn('uiLanguageLabel(code)', source)

    def test_browse_control_stays_visible_and_explains_why(self):
        flat = read(SHARED, 'ui/theme/Flat.kt')
        for required in ['IknaGlyph.BROWSE ->', 's * 0.78f', 's * 0.61f',
                         's * 0.44f', 'val x = s * 0.11f']:
            self.assertIn(required, flat)
        self.assertNotIn('IknaGlyph.STACK', flat)
        self.assertNotIn('crossed: Boolean', flat)
        for base, name in [(ANDROID, 'ui/decks/DecksScreen.kt'), (SHARED, 'ui/decks/DeckList.kt')]:
            source = read(base, name)
            for required in ['browseAvailable: Boolean', 'glyph = IknaGlyph.BROWSE',
                             'color = if (browseAvailable) accent else muted',
                             'if (browseAvailable) "a11y.012" else "a11y.015"']:
                self.assertIn(required, source)
            self.assertNotIn('crossed =', source)
            self.assertNotIn('if (onBrowse != null)', source)
        for base, name, notice in [
            (ANDROID, 'ui/decks/DecksScreen.kt', 'note'),
            (DESKTOP, 'Shell.kt', 'notice'),
        ]:
            source = read(base, name)
            self.assertIn('browseAvailable = browse.available', source)
            self.assertIn('BrowseAvailability.blocked(BrowseUnavailableReason.CHECKING)', source)
            self.assertIn('BrowseUnavailableReason.CHECK_FAILED', source)
            self.assertIn('browseUnavailableText(latest)', source)
            self.assertIn('if (latest.available)', source)
            self.assertNotIn('LOAD_GUARD', source)
        transient = read(SHARED, 'ui/theme/TransientNotice.kt')
        self.assertIn('NOTICE_MILLIS = 5_000L', transient)
        self.assertIn('widthIn(max = 560.dp)', transient)

    def test_browse_credit_is_cumulative_global_and_honest(self):
        policy = read(SHARED, 'domain/session/BrowsePolicy.kt')
        repository = read(SHARED, 'data/repo/LearningRepository.kt')
        settings = read(SHARED, 'data/prefs/SettingsStore.kt')
        daos = read(SHARED, 'data/db/Daos.kt')
        notice = read(SHARED, 'ui/session/BrowseNotice.kt')
        for required in ['POINTS_PER_BROWSE = 3', 'MAX_BANK_POINTS =',
                         'fun settleCredits(', 'day > previous',
                         'GovernorReason.FIRST_RUN,', 'MIN_SUCCESSFUL_REVIEW_DAYS = 3']:
            self.assertIn(required, policy)
        for forbidden in ['PLAN_TOO_SMALL', 'LOAD_GUARD', 'MIN_STABILITY_DAYS',
                          'fun quota(']:
            self.assertNotIn(forbidden, policy + repository)
        for required in ['settleBrowseCreditPoints', 'clearBrowseCredits',
                         'browseDao.countAll()', 'BrowsePolicy.cardsForCredits(creditPoints)',
                         'BrowseAvailability.blocked(blockers)',
                         'successfulReviewTimesForChunks']:
            self.assertIn(required, repository)
        for required in ['browseEarnedPointsV1', 'browseExposureBaselineV1',
                         'browseLastExposureCountV1', 'browseCreditedDayV1',
                         'suspend fun settleBrowseCredits(',
                         'suspend fun clearBrowseCredits()']:
            self.assertIn(required, settings)
        self.assertIn('AND rating >= 3 AND', daos)
        self.assertIn('SELECT COUNT(*) FROM browse_exposures', daos)
        self.assertIn('availability.blockers', notice)
        self.assertIn('joinToString(separator = "\\n")', notice)
        for base, name in [(ANDROID, 'AppContainer.kt'), (DESKTOP, 'DesktopContainer.kt')]:
            container = read(base, name)
            self.assertIn('learningRepository.settleBrowseCreditPoints', container)
            self.assertIn('settings.settleBrowseCredits(', container)
            self.assertIn('learningRepository.clearBrowseCredits', container)

    def test_desktop_wipe_clears_the_whole_database_and_returns_to_first_run(self):
        android = read(ANDROID, 'AppContainer.kt')
        desktop = read(DESKTOP, 'DesktopContainer.kt')
        settings = read(DESKTOP, 'SettingsPane.kt')
        database = read(SHARED, 'data/db/IknaDatabase.kt')
        factory = read(SHARED, 'data/db/IknaDatabaseFactory.kt')
        daos = read(SHARED, 'data/db/Daos.kt')
        self.assertIn('suspend fun wipeDatabase()', android)
        self.assertIn('db.wipeAllData()', android)
        for required in ['suspend fun wipeAllData()', 'db.wipeAllData()',
                         'settings.clearAll()']:
            self.assertIn(required, desktop)
        self.assertIn('abstract fun wipeDao(): WipeDao', database)
        for required in ['suspend fun IknaDatabase.wipeAllData()',
                         'inTransaction {', 'val wipe = wipeDao()']:
            self.assertIn(required, factory)
        wipe_dao = daos.split('interface WipeDao {', 1)[1]
        wiped_tables = set(re.findall(r'@Query\("DELETE FROM ([a-z_]+)"\)', wipe_dao))
        schema = read(ROOT, 'app/schemas/dev.ikna.data.db.IknaDatabase/9.json')
        schema_tables = set(re.findall(r'"tableName"\s*:\s*"([a-z_]+)"', schema))
        self.assertEqual(schema_tables, wiped_tables)
        self.assertEqual(10, len(wiped_tables))
        self.assertNotIn('clearAllTables', android + desktop)
        self.assertIn('withContext(Dispatchers.IO)', settings)
        self.assertIn('container.wipeAllData()', settings)
        self.assertIn('onWiped()', settings)
        self.assertNotIn('container.deckRepository.delete(deck.id)', settings)

    def test_wipe_queries_execute_and_empty_every_schema_nine_table(self):
        schema_path = ROOT / 'app/schemas/dev.ikna.data.db.IknaDatabase/9.json'
        entities = json.loads(schema_path.read_text(encoding='utf-8'))['database']['entities']
        database = sqlite3.connect(':memory:')
        tables = []
        for entity in entities:
            table = entity['tableName']
            tables.append(table)
            database.execute(entity['createSql'].replace('${TABLE_NAME}', table))
            columns = database.execute(f'PRAGMA table_info("{table}")').fetchall()
            names = [f'"{column[1]}"' for column in columns]
            values = []
            for column in columns:
                affinity = (column[2] or 'TEXT').upper()
                if 'INT' in affinity:
                    values.append(1)
                elif any(kind in affinity for kind in ('REAL', 'FLOA', 'DOUB')):
                    values.append(1.0)
                elif 'BLOB' in affinity:
                    values.append(b'x')
                else:
                    values.append('x')
            marks = ','.join('?' for _ in values)
            database.execute(
                f'INSERT INTO "{table}" ({",".join(names)}) VALUES ({marks})',
                values
            )
        database.commit()
        self.assertTrue(all(database.execute(
            f'SELECT COUNT(*) FROM "{table}"'
        ).fetchone()[0] == 1 for table in tables))

        daos = read(SHARED, 'data/db/Daos.kt')
        wipe_dao = daos.split('interface WipeDao {', 1)[1]
        queries = re.findall(r'@Query\("(DELETE FROM [a-z_]+)"\)', wipe_dao)
        self.assertEqual(10, len(queries))
        database.execute('BEGIN')
        for query in queries:
            database.execute(query)
        database.commit()
        self.assertTrue(all(database.execute(
            f'SELECT COUNT(*) FROM "{table}"'
        ).fetchone()[0] == 0 for table in tables))
        self.assertEqual(('ok',), database.execute('PRAGMA integrity_check').fetchone())
        database.close()

    def test_desktop_first_launch_uses_the_mobile_onboarding_contract(self):
        shell = read(DESKTOP, 'Shell.kt')
        onboarding = read(DESKTOP, 'OnboardingPane.kt')
        container = read(DESKTOP, 'DesktopContainer.kt')
        for required in ['storedSettings == null', '!settings.onboardingDone',
                         'DesktopOnboardingPane(container)', 'resetForFirstRun()']:
            self.assertIn(required, shell)
        for required in ['"onb.001"', '"onb.003"', '"onb.005"', '"onb.011"',
                         'IknaWordmark(', 'GestureDemo()', 'container.completeOnboarding()']:
            self.assertIn(required, onboarding)
        for required in ['suspend fun completeOnboarding()',
                         'packLoader.installBundledPacks()',
                         'learningRepository.ensureDailyPlan()',
                         'settings.setOnboardingDone(true)']:
            self.assertIn(required, container)

    def test_progress_names_today_and_deck_and_preserves_subpercent(self):
        session = read(SHARED, 'ui/session/SessionChrome.kt')
        for required in ['fun IknaTodayProgress(', 'S.t("progress.001")',
                         'text = "$done / $total"']:
            self.assertIn(required, session)
        for base, name in [(ANDROID, 'ui/session/SessionScreen.kt'), (DESKTOP, 'SessionPane.kt')]:
            self.assertIn('IknaTodayProgress(', read(base, name))
        deck = read(SHARED, 'ui/decks/DeckList.kt')
        for required in ['fun IknaDeckProgress(', 'S.t("progress.002")',
                         'return if (percent == 0L) "<1%"']:
            self.assertIn(required, deck)
        for base, name in [(ANDROID, 'ui/decks/DecksScreen.kt'),
                           (ANDROID, 'ui/decks/DeckScreen.kt'),
                           (DESKTOP, 'DeckPane.kt')]:
            self.assertIn('IknaDeckProgress(', read(base, name))

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
        for required in ['tools/check_localization.py', 'tools/check_design_parity.py', ':desktop:test', ':app:testReleaseUnitTest', ':app:assembleDebugAndroidTest', ':app:connectedDebugAndroidTest']:
            self.assertIn(required, source)
        source = read(ROOT, 'app/src/test/java/dev/ikna/domain/optimizer/LocalOptimizerTest.kt')
        for test in ['automaticPolicyAppliesOnlyAcceptedResultsAndHonoursMonthlyLimit', 'automaticEligibilityIsQuietAndInsufficientHistoryKeepsDefaults', 'automaticActivationCannotReviveAProfileAfterReset', 'automaticFailuresBackOffInsteadOfFittingAfterEveryAnswer']:
            self.assertIn(test, source)


    def test_deck_header_paint_and_desktop_inspector(self):
        lattice = read(SHARED, 'ui/theme/MemoryLattice.kt')
        inspector = read(SHARED, 'ui/theme/ElementInspector.kt')
        controls = read(SHARED, 'ui/theme/Flat.kt')
        deck_rows = read(SHARED, 'ui/decks/DeckList.kt')
        prefs = read(SHARED, 'data/prefs/SettingsStore.kt')
        android_home = read(ANDROID, 'ui/decks/DecksScreen.kt')
        desktop_shell = read(DESKTOP, 'Shell.kt')
        desktop_settings = read(DESKTOP, 'SettingsPane.kt')

        self.assertIn('fun IknaDeckHeaderPaint(', lattice)
        self.assertIn('val todayLeft', lattice)
        self.assertIn('fun protected(', lattice)
        self.assertIn('for (step in 0 until run)', lattice)
        for home in (android_home, desktop_shell):
            self.assertIn('IknaDeckHeaderPaint(seed = 0x5D31_7A0C)', home)

        self.assertIn('val elementInspector: Boolean = false', prefs)
        self.assertIn('booleanPreferencesKey("elementInspector")', prefs)
        self.assertIn('suspend fun setElementInspector(', prefs)
        self.assertIn('S.t("inspector.001")', desktop_settings)
        self.assertIn('settings.elementInspector', desktop_settings)
        self.assertIn('IknaElementInspector(enabled = settings.elementInspector)', desktop_shell)
        self.assertIn('.iknaInspect("IknaDesktopApp")', desktop_shell)
        self.assertIn('Alignment.BottomEnd', inspector)
        self.assertIn('collectIsHoveredAsState()', inspector)
        self.assertIn('.hoverable(interactionSource = interaction)', inspector)
        self.assertNotIn('onPointerEvent', inspector)
        self.assertIn('Stroke(width = 1.dp.toPx())', inspector)
        settings_chrome = read(SHARED, 'ui/settings/SettingsChrome.kt')
        self.assertIn('Modifier.animateContentSize(', settings_chrome)
        self.assertIn('IknaIconButton[', controls)
        self.assertIn('IknaDeckRow[', deck_rows)


if __name__ == '__main__':
    unittest.main(verbosity=2)
