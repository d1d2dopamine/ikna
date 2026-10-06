package dev.ikna.desktop;

import java.util.Properties;

/** Configure the pinned Skiko immediate Direct3D path before constructing a window. */
public final class WindowsFramePacing {
    public static final String IMMEDIATE_VSYNC =
            "skiko.rendering.windows.waitForFrameVsyncOnRedrawImmediately";

    private WindowsFramePacing() {}

    public static void configure(String osName, Properties properties) {
        if (TitleBarClicks.useCustomTitleBar(osName) && !properties.containsKey(IMMEDIATE_VSYNC)) {
            properties.setProperty(IMMEDIATE_VSYNC, "true");
        }
    }
}
