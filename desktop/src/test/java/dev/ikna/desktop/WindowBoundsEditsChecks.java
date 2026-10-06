package dev.ikna.desktop;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Properties;

/** Exact assertion code shared by JUnit and the standalone headless check. */
public final class WindowBoundsEditsChecks {
    private static final Rectangle START = new Rectangle(-1200, 50, 1180, 800);
    private static final Dimension MIN = new Dimension(1000, 705);

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void allEdgesAndCorners() {
        int[] sides = {1, 2, 4, 8, 5, 6, 9, 10};
        Rectangle[] expected = {
            new Rectangle(-1230, 50, 1210, 800), new Rectangle(-1200, 50, 1150, 800),
            new Rectangle(-1200, 30, 1180, 820), new Rectangle(-1200, 50, 1180, 780),
            new Rectangle(-1230, 30, 1210, 820), new Rectangle(-1200, 30, 1150, 820),
            new Rectangle(-1230, 50, 1210, 780), new Rectangle(-1200, 50, 1150, 780)
        };
        for (int i = 0; i < sides.length; i++) {
            require(WindowBoundsEdits.resize(START, -30, -20, sides[i], MIN).equals(expected[i]),
                    "wrong edge/corner " + sides[i]);
        }
        require(START.equals(new Rectangle(-1200, 50, 1180, 800)), "input bounds mutated");
    }

    public static void minimumAndAnchors() {
        Rectangle a = WindowBoundsEdits.resize(START, 900, 900, 5, MIN);
        require(a.equals(new Rectangle(-1020, 145, 1000, 705)), "minimum top-left anchor");
        Rectangle b = WindowBoundsEdits.resize(START, -900, -900, 10, MIN);
        require(b.equals(new Rectangle(-1200, 50, 1000, 705)), "minimum bottom-right anchor");
        Rectangle c = WindowBoundsEdits.resize(START, 0, 0, 5, MIN);
        require(c.equals(START), "drag back to origin accumulates rounding/drift");
    }

    private static final class RecordingWindow extends Component {
        int writes;
        @Override public void setBounds(int x, int y, int w, int h) {
            writes++;
            super.setBounds(x, y, w, h);
        }
    }

    public static void oneBoundsWrite() {
        RecordingWindow window = new RecordingWindow();
        window.setBounds(START);
        window.writes = 0;
        WindowBoundsEdits.applyIfChanged(window, START);
        require(window.writes == 0, "unchanged bounds must not touch the native frame");
        Rectangle target = new Rectangle(-1230, 30, 1210, 820);
        WindowBoundsEdits.applyIfChanged(window, target);
        require(window.writes == 1 && window.getBounds().equals(target), "move+resize must be one write");
    }

    public static void nativeAcknowledgement() {
        WindowBoundsEdits.Restore restore = new WindowBoundsEdits.Restore();
        Dimension size = new Dimension(1180, 800);
        Point position = new Point(-1200, 50);
        restore.request(size, position);
        size.width = 1; position.x = 1;
        Rectangle maximized = new Rectangle(0, 0, 1536, 864);
        require(restore.afterNativeResize(maximized, true, false, false) == null && restore.isPending(),
                "requested Floating must not restore a still-maximized peer");
        require(restore.afterNativeResize(maximized, false, true, false) == null && restore.isPending(),
                "stale native event must not override a newer maximize request");
        require(restore.afterNativeResize(maximized, true, true, true) == null && restore.isPending(),
                "minimized windows must not receive corrective bounds");
        require(restore.afterNativeResize(maximized, true, true, false).equals(START),
                "restore must use a copy of saved floating bounds");
        require(!restore.isPending() && restore.afterNativeResize(maximized, true, true, false) == null,
                "corrective resize must not recurse/repeat");
    }

    public static void matchingRestoreAndUnknownPosition() {
        WindowBoundsEdits.Restore restore = new WindowBoundsEdits.Restore();
        restore.request(START.getSize(), START.getLocation());
        require(restore.afterNativeResize(START, true, true, false) == null && !restore.isPending(),
                "matching native restore needs zero writes");
        restore.request(START.getSize(), null);
        Rectangle actual = new Rectangle(60, 70, 1536, 864);
        require(restore.afterNativeResize(actual, true, true, false)
                .equals(new Rectangle(60, 70, 1180, 800)), "unspecified position must keep native placement");
        restore.request(START.getSize(), START.getLocation());
        restore.cancel();
        require(restore.afterNativeResize(actual, true, true, false) == null, "cancelled transition restores");
    }

    public static void windowsPacingAndOverrides() {
        Properties properties = new Properties();
        WindowsFramePacing.configure("Windows 11", properties);
        require("true".equals(properties.getProperty(WindowsFramePacing.IMMEDIATE_VSYNC)), "Windows default");
        properties.setProperty(WindowsFramePacing.IMMEDIATE_VSYNC, "false");
        WindowsFramePacing.configure("Windows 10", properties);
        require("false".equals(properties.getProperty(WindowsFramePacing.IMMEDIATE_VSYNC)), "explicit override lost");
        for (String os : new String[] {"Linux", "Mac OS X", "Darwin", null}) {
            Properties untouched = new Properties();
            WindowsFramePacing.configure(os, untouched);
            require(untouched.isEmpty(), "changed other platform " + os);
        }
        require(properties.size() == 1, "pacing must not force a different renderer or VSync globally");
    }

    public static void main(String[] args) {
        allEdgesAndCorners(); minimumAndAnchors(); oneBoundsWrite(); nativeAcknowledgement();
        matchingRestoreAndUnknownPosition(); windowsPacingAndOverrides();
        System.out.println("PASS: 6 bounds/restore/pacing groups (including all 8 edges/corners)");
    }
}
