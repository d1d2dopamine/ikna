package dev.ikna.desktop;

/** The exact assertion code called by JUnit, also executable without Gradle. */
public final class TitleBarClicksChecks {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static boolean click(TitleBarClicks c, long time, float x, float y) {
        c.press(time, x, y, 100, 200, true);
        return c.release(time + 20, x, y, 100, 200);
    }
    public static void doubleClick() {
        TitleBarClicks c = new TitleBarClicks(500, 4);
        require(!click(c, 100, 20, 10), "a single click must not maximize");
        require(click(c, 200, 20, 10), "two close clicks must toggle placement");
        require(!click(c, 300, 20, 10), "a triple click must not toggle twice");
    }
    public static void intervalAndDistance() {
        TitleBarClicks c = new TitleBarClicks(700, 4);
        require(!click(c, 100, 20, 10), "first click");
        require(click(c, 700, 20, 10), "honour the system double-click interval");
        require(!click(c, 1000, 20, 10), "first click after a toggle");
        require(!click(c, 1100, 40, 10), "far clicks must not toggle");
        require(!click(c, 2000, 40, 10), "expired click must not toggle");
    }
    public static void dragIsNotClick() {
        TitleBarClicks c = new TitleBarClicks(500, 4);
        click(c, 100, 20, 10);
        c.press(200, 20, 10, 100, 200, true);
        c.move(80, 10, 100, 200);
        require(!c.release(220, 20, 10, 100, 200), "drag out and back is not a click");
        require(!click(c, 300, 20, 10), "drag resets the preceding click");
    }
    public static void movedFrameIsNotClick() {
        TitleBarClicks c = new TitleBarClicks(500, 4);
        click(c, 100, 20, 10);
        c.press(200, 20, 10, 100, 200, true);
        require(!c.release(220, 20, 10, 200, 200), "window movement counts even with unchanged local pointer position");
    }
    public static void secondaryAndLongPress() {
        TitleBarClicks c = new TitleBarClicks(500, 4);
        click(c, 100, 20, 10);
        c.press(200, 20, 10, 100, 200, false);
        require(!c.release(220, 20, 10, 100, 200), "right click is not a maximize action");
        c.press(300, 20, 10, 100, 200, true);
        require(!c.release(1000, 20, 10, 100, 200), "long press is not double click");
        require(!click(c, 1100, 20, 10), "long press resets previous click");
    }
    public static void windowsOnly() {
        require(TitleBarClicks.useCustomTitleBar("Windows 11"), "Windows 11");
        require(TitleBarClicks.useCustomTitleBar("windows 10"), "case-independent Windows detection");
        for (String name : new String[] {"Linux", "Mac OS X", "Darwin", "", null}) {
            require(!TitleBarClicks.useCustomTitleBar(name), "leave other OS frames unchanged: " + name);
        }
    }
    public static void main(String[] args) {
        doubleClick(); intervalAndDistance(); dragIsNotClick();
        movedFrameIsNotClick(); secondaryAndLongPress(); windowsOnly();
        System.out.println("PASS: 6 window title-bar gesture and platform checks");
    }
}
