package dev.ikna.desktop

import org.junit.Test

class WindowFrameLayoutTest {
    @Test fun firstResizedPicture() = WindowFrameLayoutChecks.firstResizedPicture()
    @Test fun steadyFramesAndScaleChanges() = WindowFrameLayoutChecks.steadyFramesAndScaleChanges()
    @Test fun nativeInvalidationAndMinimizeReturn() = WindowFrameLayoutChecks.nativeInvalidationAndMinimizeReturn()
    @Test fun exceptionsAndZeroSize() = WindowFrameLayoutChecks.exceptionsAndZeroSize()
    @Test fun recursiveRecordingIsNotCommitted() = WindowFrameLayoutChecks.recursiveRecordingIsNotCommitted()
    @Test fun boundedTraceAndShutdown() = WindowFrameLayoutChecks.boundedTraceAndShutdown()
    @Test fun failedTraceDoesNotBreakRendering() = WindowFrameLayoutChecks.failedTraceDoesNotBreakRendering()
}
