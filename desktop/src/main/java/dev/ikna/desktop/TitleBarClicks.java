package dev.ikna.desktop;

/** Non-consuming double-click recognition; moving the window is never a click. */
public final class TitleBarClicks {
    private final long interval;
    private final float slopSquared;
    private boolean tracking, dragged;
    private long pressedAt, lastClick = -1;
    private float pressX, pressY, lastX, lastY;
    private int windowX, windowY;
    public TitleBarClicks(long interval, float slop) {
        this.interval = interval > 0 ? interval : 500;
        this.slopSquared = slop * slop;
    }
    public void press(long time, float x, float y, int frameX, int frameY, boolean primary) {
        tracking = primary; dragged = false; pressedAt = time;
        pressX = x; pressY = y; windowX = frameX; windowY = frameY;
        if (!primary) lastClick = -1;
    }
    public void move(float x, float y, int frameX, int frameY) {
        if (tracking && (distance(x, y, pressX, pressY) > slopSquared
                || frameX != windowX || frameY != windowY)) dragged = true;
    }
    public boolean release(long time, float x, float y, int frameX, int frameY) {
        if (!tracking) return false;
        move(x, y, frameX, frameY);
        tracking = false;
        if (dragged || time < pressedAt || time - pressedAt > interval) {
            lastClick = -1;
            return false;
        }
        boolean twice = lastClick >= 0 && time >= lastClick && time - lastClick <= interval
                && distance(x, y, lastX, lastY) <= slopSquared;
        lastClick = twice ? -1 : time; lastX = x; lastY = y;
        return twice;
    }
    private static float distance(float x, float y, float a, float b) {
        float dx = x - a, dy = y - b;
        return dx * dx + dy * dy;
    }
    public static boolean useCustomTitleBar(String osName) {
        return osName != null && osName.regionMatches(true, 0, "Windows", 0, 7);
    }
}
