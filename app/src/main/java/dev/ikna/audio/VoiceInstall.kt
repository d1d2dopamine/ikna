package dev.ikna.audio

/*
 * How far an installation has got, and whether one is running at all.
 *
 * Both used to be one Int held by the voice screen: files copied so far. That
 * number is honest about a Piper voice, which is a dozen small files, and a lie
 * about Kokoro, which is one file of a few hundred megabytes and some crumbs --
 * the screen read "1" for as long as the unpacking took, and every user who saw
 * it concluded, reasonably, that the app had hung.
 *
 * So progress is counted in bytes of the file that was picked, reported from
 * inside the copying of a single file, and it lives out here rather than in the
 * screen: see VoiceInstaller for why that second part mattered even more.
 */

/** How much of an installation is done. */
data class VoiceInstallProgress(
    /** Files finished so far. Still the useful number for a folder of small files. */
    val files: Int = 0,
    /** Bytes taken from the source so far: from the archive, or from the folder. */
    val bytes: Long = 0L,
    /** What [bytes] is counted against, or 0 when the size could not be asked for. */
    val total: Long = 0L,
) {
    /**
     * Percent done, or -1 when there is no total and a percentage would be a
     * guess. A screen that has to guess should say the honest thing instead,
     * which is how many files have landed.
     */
    val percent: Int
        get() = if (total <= 0L) -1
        else ((bytes.coerceAtLeast(0L) * 100L) / total).coerceIn(0L, 100L).toInt()
}

/** Whether a model is being installed, and what came of the last attempt. */
sealed interface VoiceInstallState {

    /** Nothing running, nothing left to report. */
    object Idle : VoiceInstallState

    data class Running(val progress: VoiceInstallProgress) : VoiceInstallState

    /** Finished. Held until a screen has shown it, then cleared by acknowledge(). */
    data class Done(val result: VoiceModelResult) : VoiceInstallState
}
