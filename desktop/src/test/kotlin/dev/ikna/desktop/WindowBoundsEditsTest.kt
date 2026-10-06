package dev.ikna.desktop

import org.junit.Test

class WindowBoundsEditsTest {
    @Test fun allEdgesAndCorners() = WindowBoundsEditsChecks.allEdgesAndCorners()
    @Test fun minimumAndAnchors() = WindowBoundsEditsChecks.minimumAndAnchors()
    @Test fun oneBoundsWrite() = WindowBoundsEditsChecks.oneBoundsWrite()
    @Test fun nativeAcknowledgement() = WindowBoundsEditsChecks.nativeAcknowledgement()
    @Test fun matchingRestoreAndUnknownPosition() = WindowBoundsEditsChecks.matchingRestoreAndUnknownPosition()
    @Test fun windowsPacingAndOverrides() = WindowBoundsEditsChecks.windowsPacingAndOverrides()
}
