package dev.ikna.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.update.UpdateCheck
import dev.ikna.data.update.UpdateRelease
import dev.ikna.data.update.megabytes
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import kotlinx.coroutines.launch

/*
 * The one place the app admits there is a world outside the phone.
 *
 * It tells somebody who installed an APK by hand that a newer one exists, which
 * is the one thing an app outside a store cannot otherwise do -- and, since this
 * version, it carries out the answer as well: pressing update downloads the file
 * here, with a band and a percentage, and hands it to the system installer,
 * which puts the new version over the old one and leaves the review log alone.
 *
 * Closing the window is still a complete answer. Nothing is downloaded before
 * the button is pressed, nothing is installed without the platform's own prompt,
 * and the release page is one tap away in every state this window can be in.
 */

/** The version of the running build, as its own manifest names it. */
fun installedVersion(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

/** Whether this phone can run the ordinary APK rather than the 32-bit one. */
fun has64Bit(): Boolean = Build.SUPPORTED_64_BIT_ABIS.orEmpty().isNotEmpty()

/**
 * Hands a link to whatever opens links here.
 *
 * No longer how an update arrives -- the app fetches the file itself now -- but
 * still how it arrives when that fails, and how the release page is opened.
 * A download the app cannot finish must never be the end of the road: the
 * browser could always do this, and it can still.
 */
fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Asks once per launch, at most once a day, and only if allowed to ask at all.
 *
 * Sits above every screen rather than on the home one: the app can be opened
 * straight into a session by the widget or the reminder, and an update that only
 * announced itself on the deck list would never be seen by the person who
 * studies from the notification.
 *
 * A version that was skipped is not asked about again. The day stamp is written
 * whether or not anything was found, so a phone with no network does not retry
 * on every launch.
 */
@Composable
fun UpdateGate(container: AppContainer, settings: IknaSettings) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<UpdateRelease?>(null) }
    var asked by remember { mutableStateOf(false) }

    LaunchedEffect(settings.updateCheck, settings.updateCheckedAt) {
        if (asked || !settings.updateCheck) return@LaunchedEffect
        val now = System.currentTimeMillis()
        // A clock moved backwards must not lock the check out for a day.
        val since = now - settings.updateCheckedAt
        if (since in 0 until UpdateCheck.CHECK_EVERY_MS) return@LaunchedEffect
        asked = true
        val release = UpdateCheck(installedVersion(context), has64Bit()).latest()
        container.settings.markUpdateChecked(now)
        if (release != null && release.version != settings.updateSkipped) found = release
    }

    val release = found ?: return
    UpdateSheet(
        installed = installedVersion(context),
        release = release,
        onSkip = {
            scope.launch {
                container.settings.skipUpdate(release.version, System.currentTimeMillis())
            }
            found = null
        }
    )
}

/**
 * What is available, how big it is, what changed, and two small words.
 *
 * The notes come from the release itself rather than from a string in the app:
 * the build being offered knows what is in it and this one does not. They scroll
 * inside a fixed box, so a long release cannot push the buttons off the screen.
 *
 * There is no third button. Dismissing by tapping outside behaves like skip
 * without remembering it -- the update comes back tomorrow, which is the right
 * answer for a tap that may not have been aimed at anything. While bytes are
 * arriving that tap does nothing instead: a download cancelled by a misplaced
 * finger, at eighty percent, is the worst thing this window could do.
 *
 * Once update is pressed the two words are replaced by the download itself --
 * the band, the percentage, the megabytes -- and the window stays where it is
 * until the installer takes over.
 */
@Composable
fun UpdateSheet(
    installed: String,
    release: UpdateRelease,
    onSkip: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val notes = rememberScrollState()
    val download = rememberUpdateDownload()
    Dialog(onDismissRequest = { if (!download.running) onSkip() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, ink)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = S.t("upd.001"),
                style = MaterialTheme.typography.labelLarge,
                color = muted
            )
            Spacer(Modifier.height(10.dp))
            // The version being left and the version being offered, in that
            // order, because the question a person actually has is not "what is
            // new" but "how far behind am I".
            Text(
                text = installed + "  \u2192  " + release.version,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = S.t("upd.002") + megabytes(release.sizeBytes) + " " + S.t("upd.003"),
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
            if (release.notes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = S.t("upd.004"),
                    style = MaterialTheme.typography.labelLarge,
                    color = muted
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(notes)
                ) {
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (download.phase == UpdatePhase.IDLE) {
                Text(
                    text = S.t("upd.007"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IknaTextButton(label = S.t("upd.006"), onClick = onSkip, color = muted)
                    Spacer(Modifier.width(20.dp))
                    IknaTextButton(
                        label = S.t("upd.005"),
                        onClick = { download.start(release) },
                        color = ink
                    )
                }
            } else {
                UpdateDownloadPanel(
                    state = download,
                    release = release,
                    onBrowser = { openInBrowser(context, release.apkUrl) }
                )
            }
        }
    }
}
