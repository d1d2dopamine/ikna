package dev.ikna.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The actual assertions used by SettingsLazyLayoutTest and MotionPolishTest.
 *
 * These have always been source contracts, not Compose runtime tests. Keep
 * their implementation dependency-free so the SAME checks can also run before
 * Gradle, with: java --source 17 app/src/test/java/dev/ikna/ui/SettingsSourceContracts.java
 * Keep this helper within Java 8 library APIs: Android's javac API surface
 * is not the same as a full JDK 17, even for host-side unit-test sources.
 * CI compiles with --release 8 before running the resulting class.
 * Missing sources fail; no test is silently skipped. No production code lives here.
 */
public final class SettingsSourceContracts {
    private static final String ANDROID = "app/src/main/java/dev/ikna/";
    private static final String SHARED = "shared/src/jvmShared/kotlin/dev/ikna/";
    private static final String DESKTOP = "desktop/src/main/kotlin/dev/ikna/desktop/";
    private final Path root;

    public SettingsSourceContracts() {
        this(findRoot(Paths.get("")));
    }

    public SettingsSourceContracts(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private static Path findRoot(Path start) {
        for (Path here = start.toAbsolutePath().normalize(); here != null; here = here.getParent()) {
            if (Files.isRegularFile(here.resolve("settings.gradle.kts"))
                    && Files.isDirectory(here.resolve("app"))
                    && Files.isDirectory(here.resolve("shared"))) {
                return here;
            }
        }
        throw new AssertionError("Cannot find the repository root above " + start.toAbsolutePath());
    }

    private String source(String relative) {
        try {
            // Language level 17 does not make Java 11 library APIs available
            // in the Android compile classpath. readAllBytes is available there.
            return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new AssertionError("Source was not found or could not be read: " + root.resolve(relative), failure);
        }
    }

    private static void has(String text, String fragment, String description) {
        if (!text.contains(fragment)) {
            throw new AssertionError(description + "\nMissing source fragment: " + fragment);
        }
    }

    private static void lacks(String text, String fragment, String description) {
        if (text.contains(fragment)) {
            throw new AssertionError(description + "\nUnexpected source fragment: " + fragment);
        }
    }

    private static void intValue(String text, String name, int value) {
        String regex = "\\b" + Pattern.quote(name) + "\\s*=\\s*" + value + "\\b";
        if (!Pattern.compile(regex).matcher(text).find()) {
            throw new AssertionError("Expected " + name + " = " + value + " exactly");
        }
    }

    private static void count(String text, String regex, long expected, String description) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        long actual = 0;
        while (matcher.find()) {
            actual++;
        }
        if (actual != expected) {
            throw new AssertionError(description + ": expected " + expected + ", found " + actual);
        }
    }

    public void offscreenSettingsAreNotComposedEagerly() {
        String android = source(ANDROID + "ui/settings/SettingsScreen.kt");
        has(android, "LazyColumn(", "Android settings must stay lazy");
        has(android, "rememberLazyListState()", "Android must retain its lazy-list state");
        lacks(android, ".verticalScroll(", "Do not restore an eager Android settings document");
        lacks(android, "private fun Anchored(", "Do not restore measured global section anchors");
        count(android, "item\\(key = ID_", 9, "Android settings section count");

        String desktop = source(DESKTOP + "SettingsPane.kt");
        has(desktop, "LazyColumn(", "Desktop must use the same lazy section model");
        has(desktop, "rememberLazyListState()", "Desktop must retain its lazy-list state");
        lacks(desktop, ".verticalScroll(", "Do not restore an eager desktop settings document");
        count(desktop, "item\\(key = \"", 9, "Desktop settings section count");
    }

    public void jumpStripTargetsLazyItemsWithoutGlobalSectionMeasurement() {
        String android = source(ANDROID + "ui/settings/SettingsScreen.kt");
        String chrome = source(SHARED + "ui/settings/SettingsChrome.kt");
        String desktop = source(DESKTOP + "SettingsPane.kt");
        // Item observation moved to shared; destination scrolling stays in the host.
        has(chrome, "firstVisibleItemIndex", "The shared jump strip must follow the active lazy item");
        has(android, "IknaSettingsJumpRow(", "Android must actually use the shared jump strip");
        has(android, "listState = listState", "Android must pass its real list state");
        has(android, "animateScrollToItem(targetIndex)", "Animated Android section navigation");
        has(android, "scrollToItem(targetIndex)", "Non-animated Android section navigation");
        lacks(android, "Anchored(ID_", "Navigation must not depend on global pixel measurement");
        has(desktop, "IknaSettingsJumpRow(sections, listState, settings.animations", "Desktop must use shared observation");
        has(desktop, "animateScrollToItem(index)", "Animated desktop section navigation");
        has(desktop, "scrollToItem(index)", "Non-animated desktop section navigation");
    }

    public void speechEngineWaitsUntilItsSectionIsVisible() {
        String android = source(ANDROID + "ui/settings/SettingsScreen.kt");
        has(android, "speechSectionVisible", "Speech setup must depend on section visibility");
        has(android, "!settings.speechEnabled || !speechSectionVisible", "Disabled or offscreen speech must not initialize");
    }

    public void fastSettingsFlingDoesNotStartACompetingJumpAnimation() {
        String android = source(ANDROID + "ui/settings/SettingsScreen.kt");
        String chrome = source(SHARED + "ui/settings/SettingsChrome.kt");
        has(chrome, "fun IknaSettingsJumpRow(", "The shared jump-strip wrapper must exist");
        has(chrome, "derivedStateOf { listState.isScrollInProgress }", "Observe scrolling in a small recomposition scope");
        has(chrome, "verticalScrolling = verticalScrolling", "Forward the fling state to the shared row");
        has(chrome, "LaunchedEffect(activeId, rowWidth, animations, settled, verticalScrolling)", "Recentre only when the relevant state changes");
        String guard = "if (!settled || verticalScrolling || rowWidth == 0) return@LaunchedEffect";
        has(chrome, guard, "A vertical fling must prevent a competing horizontal animation");
        has(chrome, "row.animateScrollTo(", "Keep animated recentring when the list settles");
        has(chrome, "row.scrollTo(target)", "Respect the disabled-animation setting");
        if (chrome.indexOf(guard) >= chrome.indexOf("row.animateScrollTo(")) {
            throw new AssertionError("The fling guard must run before starting horizontal scroll animation");
        }
        has(android, "IknaSettingsJumpRow(", "The guarded shared strip must be connected on Android");
        has(android, "listState = listState", "The strip must observe the actual Android list");
        lacks(android, "return@JumpRow", "No obsolete composable callback label");
        lacks(android, "return@IknaJumpRow", "Do not replace an obsolete callback label with another invalid label");
        long typedItems = 0;
        for (String line : android.split("\\r\\n|\\r|\\n", -1)) {
            if (line.contains("item(key = ID_")
                    && line.contains("contentType = SETTINGS_SECTION_CONTENT_TYPE")) {
                typedItems++;
            }
        }
        if (typedItems != 9) {
            throw new AssertionError("All nine Android lazy sections must retain a common content type; found " + typedItems);
        }
    }

    public void autoLoadTargetIsPublishedOnlyAfterMeasurementIsKnown() {
        String android = source(ANDROID + "ui/settings/SettingsScreen.kt");
        has(android, "mutableStateOf<Int?>(null)", "The auto target must start as unknown, not as a guessed number");
        has(android, "val measured = container.learningRepository.normIsMeasured()", "Ask whether the history is sufficient");
        has(android, "measuredNorm = target.takeIf { measured && it > 0 }", "Publish only a known positive measured target");
        lacks(android, "var normMeasured", "Do not publish measurement and value as competing mutable state");
    }

    public void microMotionIsShortLocalAndObeysTheExistingSwitch() {
        String metrics = source(SHARED + "ui/theme/Metrics.kt");
        String theme = source(SHARED + "ui/theme/Theme.kt");
        String main = source(ANDROID + "MainActivity.kt");
        String flat = source(SHARED + "ui/theme/Flat.kt");
        String chrome = source(SHARED + "ui/settings/SettingsChrome.kt");
        has(metrics, "LocalIknaMotionEnabled", "Keep the shared motion policy");
        intValue(metrics, "controlChangeDurationMillis", 160);
        intValue(metrics, "contentChangeDurationMillis", 200);
        intValue(metrics, "progressChangeDurationMillis", 260);
        has(theme, "LocalIknaMotionEnabled provides motionEnabled", "Provide the selected motion policy to shared controls");
        has(main, "motionEnabled = settings.animations", "Connect the existing Android setting to the theme");
        has(flat, "animateDpAsState(", "Keep local size transitions");
        has(flat, "animateColorAsState(", "Keep local colour transitions");
        has(flat, "animateFloatAsState(", "Keep local alpha/progress transitions");
        // Section height animation now belongs to the shared renderer, not the host page.
        has(chrome, "Modifier.animateContentSize(", "Keep the settings section height transition");
        has(chrome, "LocalIknaMotionEnabled.current", "The shared section must read the selected motion policy");
        has(chrome, "durationMillis = Motion.contentChangeDurationMillis", "Reuse the common short duration");
        has(chrome, "else snap()", "Disabled motion must not animate section height");
        has(source(ANDROID + "ui/settings/SettingsScreen.kt"), "IknaSettingsSection(", "Android must use the animated shared section");
        has(source(DESKTOP + "SettingsPane.kt"), "IknaSettingsSection(", "Desktop must use the same animated shared section");
    }

    public static void main(String[] args) {
        SettingsSourceContracts contracts = args.length == 0
                ? new SettingsSourceContracts()
                : new SettingsSourceContracts(Paths.get(args[0]));
        contracts.offscreenSettingsAreNotComposedEagerly();
        System.out.println("PASS: lazy settings on Android and desktop");
        contracts.jumpStripTargetsLazyItemsWithoutGlobalSectionMeasurement();
        System.out.println("PASS: shared observation and host lazy-item navigation");
        contracts.speechEngineWaitsUntilItsSectionIsVisible();
        System.out.println("PASS: lazy speech initialization");
        contracts.fastSettingsFlingDoesNotStartACompetingJumpAnimation();
        System.out.println("PASS: fling suppression and settled recentring");
        contracts.autoLoadTargetIsPublishedOnlyAfterMeasurementIsKnown();
        System.out.println("PASS: measured auto-load target publication");
        contracts.microMotionIsShortLocalAndObeysTheExistingSwitch();
        System.out.println("PASS: short, local, optional motion in shared controls");
    }
}
