package dev.ikna.desktop;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

/** Logical AWT coordinates, including negative monitor origins; no DPI conversion. */
public final class WindowBoundsEdits {
    public static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8;

    private WindowBoundsEdits() {}

    /** Derive every drag step from its initial bounds, keeping the opposite edge fixed. */
    public static Rectangle resize(Rectangle start, int dx, int dy, int sides, Dimension minimum) {
        int width = start.width;
        int height = start.height;
        int x = start.x;
        int y = start.y;
        if ((sides & LEFT) != 0) {
            width = Math.max(Math.max(1, minimum.width), start.width - dx);
            x = start.x + start.width - width;
        } else if ((sides & RIGHT) != 0) {
            width = Math.max(Math.max(1, minimum.width), start.width + dx);
        }
        if ((sides & TOP) != 0) {
            height = Math.max(Math.max(1, minimum.height), start.height - dy);
            y = start.y + start.height - height;
        } else if ((sides & BOTTOM) != 0) {
            height = Math.max(Math.max(1, minimum.height), start.height + dy);
        }
        return new Rectangle(x, y, width, height);
    }

    /** One native bounds write, never a visible move followed by a separate resize. */
    public static void applyIfChanged(Component window, Rectangle bounds) {
        if (!window.getBounds().equals(bounds)) window.setBounds(bounds);
    }

    /** A requested placement is not a native acknowledgement. Drain only on a native resize. */
    public static final class Restore {
        private Dimension size;
        private Point position;

        public boolean isPending() { return size != null; }

        public void request(Dimension size, Point position) {
            this.size = new Dimension(size);
            this.position = position == null ? null : new Point(position);
        }

        public void cancel() { size = null; position = null; }

        public Rectangle afterNativeResize(Rectangle actual, boolean requestedFloating,
                                            boolean nativeFloating, boolean minimized) {
            if (!isPending() || !requestedFloating || !nativeFloating || minimized) return null;
            Rectangle target = new Rectangle(position == null ? actual.getLocation() : position, size);
            cancel(); // Before applying bounds: its resize event must not restore recursively.
            return target.equals(actual) ? null : target;
        }
    }
}
