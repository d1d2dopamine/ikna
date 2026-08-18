package dev.ikna.ui.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ikna.data.update.UpdateDownload
import dev.ikna.data.update.UpdateRelease
import dev.ikna.data.update.canInstallPackages
import dev.ikna.data.update.installApk
import dev.ikna.data.update.megabytes
import dev.ikna.data.update.openInstallPermission
import dev.ikna.data.update.progressFraction
import dev.ikna.data.update.progressPercent
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/*
 * What the update window shows while it is working.
 *
 * One state holder and one block of interface, used by both places that can
 * offer an update -- the window over every screen, and Settings -> Updates --
 * because a download that looks like two different things depending on where it
 * was started from is two things to get wrong.
 */

/** Where a download has got to. */
enum class UpdatePhase {
    /** Nothing has been asked for yet. */
    IDLE,

    /** Bytes are arriving; the band and the percentage are live. */
    RUNNING,

    /** The file is on disk and the installer has been handed it. */
    READY,

    /** Downloaded, but the platform has not been told this app may install. */
    BLOCKED,

    /** The network, the disk or the installer said no. */
    FAILED
}

/**
 * The download, as far as the interface is concerned.
 *
 * Deliberately not a ViewModel: it belongs to the window that started it, it
 * holds a job and a file and nothing that should outlive them, and a download
 * that survived the window being closed would be a background download nobody
 * asked for.
 */
class UpdateDownloadState(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var phase by mutableStateOf(UpdatePhase.IDLE)
        private set

    var readBytes by mutableStateOf(0L)
        private set

    var totalBytes by mutableStateOf(0L)
        private set

    private var file: File? = null
    private var job: Job? = null

    val percent: Int get() = progressPercent(readBytes, totalBytes)
    val fraction: Float get() = progressFraction(readBytes, totalBytes)

    /** True while the window must not be dismissed by a tap outside it. */
    val running: Boolean get() = phase == UpdatePhase.RUNNING

    /**
     * Starts the download, or does nothing if one is already running.
     *
     * The size from the release page is used until the server declares its own,
     * so the band has a length from the first byte rather than sitting empty
     * while the connection is made.
     */
    fun start(release: UpdateRelease) {
        if (phase == UpdatePhase.RUNNING) return
        readBytes = 0L
        totalBytes = release.sizeBytes
        phase = UpdatePhase.RUNNING
        job = scope.launch {
            val downloaded = UpdateDownload(context).fetch(release) { read, total ->
                readBytes = read
                if (total > 0L) totalBytes = total
            }
            file = downloaded
            if (downloaded == null) {
                phase = UpdatePhase.FAILED
            } else {
                // Straight into the installer: the download finishing and the
                // question "install this?" are one event to the person watching,
                // and a second button in between is a step that adds nothing.
                install()
            }
        }
    }

    /**
     * Hands the finished file to the system installer.
     *
     * Separate from [start] because the installer can be closed without
     * installing -- a phone call, a misread prompt -- and the file is still
     * there, so the window can offer it again without downloading it twice.
     */
    fun install() {
        val ready = file ?: return
        if (!canInstallPackages(context)) {
            phase = UpdatePhase.BLOCKED
            return
        }
        phase = if (installApk(context, ready)) UpdatePhase.READY else UpdatePhase.FAILED
    }

    /** Opens the settings screen that allows installing from this app. */
    fun grantInstallPermission() = openInstallPermission(context)

    /** Stops the download and forgets it. Pressed, or the window being closed. */
    fun cancel() {
        job?.cancel()
        job = null
        file = null
        readBytes = 0L
        totalBytes = 0L
        phase = UpdatePhase.IDLE
    }
}

@Composable
fun rememberUpdateDownload(): UpdateDownloadState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { UpdateDownloadState(context, scope) }
}

/**
 * The band, the percentage, and whatever there is to press.
 *
 * The band is the same one the session screen uses, with its track switched on:
 * here an empty track is not chrome, it is the length of the thing being waited
 * for. The percentage is spelled out beside it, and under it the megabytes, since
 * "47%" of an unknown size tells somebody on a metered connection nothing.
 *
 * Every failure keeps the browser and the release page within reach. The app
 * downloading the file is a convenience; it is not allowed to become the only
 * way to get it.
 */
@Composable
fun UpdateDownloadPanel(
    state: UpdateDownloadState,
    release: UpdateRelease,
    onBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        when (state.phase) {
            UpdatePhase.IDLE -> Unit

            UpdatePhase.RUNNING -> {
                Text(
                    text = S.t("upd.017") + "  " + state.percent + "%",
                    style = MaterialTheme.typography.labelLarge,
                    color = ink
                )
                Spacer(Modifier.height(10.dp))
                IknaProgress(
                    fraction = state.fraction,
                    height = 6.dp,
                    color = ink,
                    track = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = (if (state.readBytes > 0L) megabytes(state.readBytes) else "0.0") +
                        " / " + megabytes(state.totalBytes) + " " + S.t("upd.003"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IknaTextButton(
                        label = S.t("upd.019"),
                        onClick = { state.cancel() },
                        color = muted
                    )
                }
            }

            UpdatePhase.READY -> {
                Text(
                    text = S.t("upd.018"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(12.dp))
                IknaWideButton(
                    label = S.t("upd.020"),
                    height = 52.dp,
                    onClick = { state.install() }
                )
            }

            UpdatePhase.BLOCKED -> {
                Text(
                    text = S.t("upd.021"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(12.dp))
                IknaWideButton(
                    label = S.t("upd.022"),
                    height = 52.dp,
                    onClick = { state.grantInstallPermission() }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IknaTextButton(
                        label = S.t("upd.020"),
                        onClick = { state.install() },
                        color = ink
                    )
                }
            }

            UpdatePhase.FAILED -> {
                Text(
                    text = S.t("upd.023"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IknaTextButton(
                        label = S.t("upd.025"),
                        onClick = onBrowser,
                        color = muted
                    )
                    Spacer(Modifier.width(20.dp))
                    IknaTextButton(
                        label = S.t("upd.024"),
                        onClick = { state.start(release) },
                        color = ink
                    )
                }
            }
        }
    }
}
