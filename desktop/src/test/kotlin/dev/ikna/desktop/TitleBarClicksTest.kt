package dev.ikna.desktop

import org.junit.Test

class TitleBarClicksTest {
    @Test fun doubleClick() = TitleBarClicksChecks.doubleClick()
    @Test fun intervalAndDistance() = TitleBarClicksChecks.intervalAndDistance()
    @Test fun dragIsNotClick() = TitleBarClicksChecks.dragIsNotClick()
    @Test fun movedFrameIsNotClick() = TitleBarClicksChecks.movedFrameIsNotClick()
    @Test fun secondaryAndLongPress() = TitleBarClicksChecks.secondaryAndLongPress()
    @Test fun windowsOnly() = TitleBarClicksChecks.windowsOnly()
}
