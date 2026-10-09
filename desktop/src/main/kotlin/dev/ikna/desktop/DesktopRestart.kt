package dev.ikna.desktop

import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

internal const val RESTART_AFTER_PID = "--restart-after-pid="

/** A successor waits before opening Room or claiming the instance lock. */
internal fun awaitPreviousProcess(args: Array<String>) {
    val values = args.filter { it.startsWith(RESTART_AFTER_PID) }
    if (values.isEmpty()) return
    require(values.size == 1)
    val pid = values.single().removePrefix(RESTART_AFTER_PID).toLong()
    require(pid > 0 && pid != ProcessHandle.current().pid())
    ProcessHandle.of(pid).orElse(null)?.onExit()?.get(45, TimeUnit.SECONDS)
}

/** No shell parsing: spaced paths and options remain individual arguments. */
internal fun desktopRestartCommand(
    packagedLauncher: String?, javaExecutable: String, vmArguments: List<String>,
    classpath: String, pid: Long
): List<String> {
    require(pid > 0)
    val launch = if (!packagedLauncher.isNullOrBlank()) listOf(packagedLauncher) else {
        require(classpath.isNotBlank())
        val reusable = vmArguments.filterNot { it.startsWith("-agentlib:jdwp") || it.startsWith("-Xrunjdwp") }
        listOf(javaExecutable) + reusable + listOf("-cp", classpath, "dev.ikna.desktop.MainKt")
    }
    return launch + (RESTART_AFTER_PID + pid)
}

/** Arrange the successor first. A failed launch leaves the current UI open. */
internal fun arrangeDesktopRestart(home: File) {
    val supervised = System.getenv("IKNA_PROFILE_RESTART_FILE")?.takeIf { it.isNotBlank() }
    if (supervised != null) {
        File(supervised).writeText("RESTART", Charsets.UTF_8)
        return
    }
    check(System.getenv("IKNA_HOT_RELOAD_GRADLE_EXE").isNullOrBlank()) {
        "Restart dev-hot-reload.cmd once to enable profile switching"
    }
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val command = desktopRestartCommand(
        System.getenv("APPIMAGE") ?: System.getProperty("jpackage.app-path"),
        File(System.getProperty("java.home"), "bin/" + if (windows) "java.exe" else "java").absolutePath,
        ManagementFactory.getRuntimeMXBean().inputArguments,
        System.getProperty("java.class.path"), ProcessHandle.current().pid()
    )
    check(File(command.first()).isFile) { "Application restart launcher is missing" }
    ProcessBuilder(command).redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(iknaLogFile(home))).start()
}
