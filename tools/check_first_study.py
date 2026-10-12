#!/usr/bin/env python3
"""First-launch source wiring checks, not Kotlin execution or device evidence."""
from pathlib import Path
import json
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / 'app/src/main/java/dev/ikna'
DESKTOP = ROOT / 'desktop/src/main/kotlin/dev/ikna/desktop'
SHARED = ROOT / 'shared/src/jvmShared/kotlin/dev/ikna'


class FirstStudyContracts(unittest.TestCase):
    def test_manifest_matches_the_starter_explanation(self):
        packs = json.loads((ROOT / 'app/src/main/assets/packs/manifest.json').read_text())['packs']
        self.assertEqual(1, len(packs), 'Revisit first-launch copy when starter changes')
        self.assertEqual(('en', 'ru', 'catalog-en-ru-beginner'),
                         (packs[0]['lang'], packs[0]['sourceLang'], packs[0]['id']))
        english = (SHARED / 'ui/text/StringsEn.kt').read_text()
        self.assertIn('beginner English with Russian meanings, if included in this build', english)

    def test_both_screens_keep_story_scrollability_choice_and_safe_retry(self):
        for path, prepare in [
            (ANDROID / 'ui/onboarding/OnboardingScreen.kt', 'prepareFirstSession('),
            (DESKTOP / 'OnboardingPane.kt', 'container.completeOnboarding()')
        ]:
            with self.subTest(path=path.name):
                source = path.read_text()
                slides = re.findall(r'(?:Slide|DesktopOnboardingSlide)\("(onb\.\d+)", "(onb\.\d+)"', source)
                self.assertEqual([('onb.001', 'onb.002'), ('onb.003', 'onb.004'),
                                  ('onb.005', 'onb.006'), ('onb.011', 'onb.012')], slides)
                self.assertIn('IknaScrollableColumn(', source)
                self.assertIn('scrollKey = step', source)
                self.assertIn('S.t("onb.014")', source)
                self.assertIn('S.t("onb.015")', source)
                self.assertIn('finish(FirstStudyDestination.CATALOGUE)', source)
                self.assertIn('finish(if (failed) destination else FirstStudyDestination.SESSION)', source)
                self.assertIn('if (busy) return', source)
                self.assertEqual(2, source.count('enabled = !busy'))
                finish = source[source.index('    fun finish('):source.index('    IknaScrollableColumn(')
                                if '    IknaScrollableColumn(' in source else source.index('    Box(')]
                self.assertLess(finish.index(prepare), finish.index('onDone(destination)'))
                self.assertIn('throw cancelled', finish)
                self.assertIn('failed = true', finish)

    def test_route_mappings_and_back_stack_are_explicit(self):
        source = (ANDROID / 'ui/nav/IknaNavHost.kt').read_text()
        self.assertIn('FirstStudyDestination.SESSION -> session(null)', source)
        self.assertIn('FirstStudyDestination.CATALOGUE -> CATALOG', source)
        callback = source[source.index('onDone = { destination ->'):source.index('composable(Routes.HOME)')]
        self.assertLess(callback.index('navigate(Routes.HOME)'), callback.index('navigate(Routes.firstStudy(destination))'))
        self.assertIn('popUpTo(Routes.ONBOARDING) { inclusive = true }', callback)
        self.assertIn('startDestination = if (known) Routes.HOME else Routes.ONBOARDING', source)
        desktop = (DESKTOP / 'Shell.kt').read_text()
        self.assertIn('ui.completeFirstRun(destination)', desktop)
        self.assertIn('!settings.onboardingDone || firstRunVisible', desktop)
        self.assertIn('firstRunVisible = true', desktop)
        callback = desktop[desktop.index('DesktopOnboardingPane(container)'):]
        self.assertLess(callback.index('ui.completeFirstRun(destination)'),
                        callback.index('firstRunVisible = false'))
        self.assertIn('FirstStudyDestination.SESSION -> Pane.SESSION', desktop)
        self.assertIn('FirstStudyDestination.CATALOGUE -> Pane.CATALOG', desktop)

    def test_preparation_still_accepts_optional_absence_only(self):
        source = (SHARED / 'data/pack/PackLoader.kt').read_text()
        self.assertRegex(source, r'catch \(_: FileNotFoundException\)\s*\{\s*continue')
        prepare = (SHARED / 'data/repo/FirstSessionPreparation.kt').read_text()
        self.assertLess(prepare.index('    installPacks()'), prepare.index('    preparePlan()'))
        self.assertLess(prepare.index('    preparePlan()'), prepare.index('    markComplete()'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
