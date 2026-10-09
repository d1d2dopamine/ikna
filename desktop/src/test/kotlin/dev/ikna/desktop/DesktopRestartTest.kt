package dev.ikna.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopRestartTest {
    @Test
    fun packagedLaunchUsesItsOwnExecutableAndWaitsForThePreviousProcess() {
        assertEquals(listOf("C:/Program Files/Ikna/Ikna.exe", "--restart-after-pid=123"),
            desktopRestartCommand("C:/Program Files/Ikna/Ikna.exe", "unused", emptyList(), "", 123))
    }

    @Test
    fun ordinaryJvmLaunchPreservesArgumentsWithoutShellQuotingOrReusingDebugPorts() {
        assertEquals(listOf("C:/Java runtime/bin/java.exe", "-Xmx1g", "-Dtest=spaced value",
            "-cp", "C:/repo with spaces/app.jar;C:/lib/lib.jar", "dev.ikna.desktop.MainKt",
            "--restart-after-pid=321"), desktopRestartCommand(null,
            "C:/Java runtime/bin/java.exe", listOf("-Xmx1g", "-Dtest=spaced value",
                "-agentlib:jdwp=transport=dt_socket,address=5005"),
            "C:/repo with spaces/app.jar;C:/lib/lib.jar", 321))
    }

    @Test
    fun invalidWaitTargetsCannotBlockOrKillTheCurrentProcess() {
        awaitPreviousProcess(emptyArray())
        assertThrows(IllegalArgumentException::class.java) {
            awaitPreviousProcess(arrayOf(RESTART_AFTER_PID + ProcessHandle.current().pid()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            awaitPreviousProcess(arrayOf(RESTART_AFTER_PID + "1", RESTART_AFTER_PID + "2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            desktopRestartCommand(null, "java", emptyList(), "", 10)
        }
    }
}
