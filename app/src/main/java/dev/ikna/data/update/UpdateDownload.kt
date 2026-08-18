package dev.ikna.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/*
 * Fetching the file the release page offers, and handing it to the installer.
 *
 * Until this version the update button opened the browser, and the reasoning was
 * written down at length: the browser downloads, the browser is trusted, and the
 * app needs no install permission. What that left out is what the person on the
 * other end actually sees -- an app that leaves, a download that lands in a
 * folder they then have to find, and a notification that looks like every other
 * file they have ever ignored. Half of them never came back.
 *
 * So the download happens here, in the window that offered it, with a band that
 * fills and a number that says how far along it is. Two things follow from that
 * and both are deliberate:
 *
 * - The file goes to the app's own cache, under updates/, and it is the only
 *   thing in there. An interrupted attempt is rubbish, not a resume point: a
 *   half APK that kept its name would be handed to the installer and refused,
 *   which reads as "the update is broken" rather than "the network dropped".
 * - The install itself is still the platform's. This code writes a file and
 *   points the system installer at it; the dialog that asks, the signature
 *   check that decides and the update-in-place that keeps the review log are all
 *   Android's, exactly as they were when the browser handed over the same file.
 *   The new version installs over the old one because the signing key is the
 *   same, which is why docs/KEYSTORE.md exists.
 *
 * Nothing here is silent. The window reports every step, a failure says so and
 * offers the browser and the release page, and the last word before anything is
 * written to the system belongs to the installer's own prompt.
 */

/** The one folder an update is ever written to, inside the app's cache. */
const val UPDATE_DIR: String = "updates"

private const val CONNECT_TIMEOUT_MS = 15_000

/*
 * Longer than the check's timeout, and for the opposite reason. A check that is
 * slow is not worth waiting for; a download that is slow is exactly what a
 * progress band is for. This is the gap between two reads, not the length of the
 * download, so a phone on a bad connection is given a minute of silence before
 * the attempt is called dead.
 */
private const val READ_TIMEOUT_MS = 60_000
private const val BUFFER_BYTES = 64 * 1024

/**
 * A ceiling, because a download with nowhere to stop is a way to fill a phone.
 * The largest file this project has ever published was 114 MB and the current
 * ones are about 40; 400 leaves room for an epoch that grows without leaving
 * room for a redirect to something that is not an APK at all.
 */
private const val MAX_APK_BYTES: Long = 400L * 1024L * 1024L

/** How full the band is drawn. Unknown length means nothing is drawn. */
fun progressFraction(read: Long, total: Long): Float {
    if (total <= 0L || read <= 0L) return 0f
    return (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/**
 * The number beside the band, 0 to 100.
 *
 * It never reaches 100 before the file is whole: the fraction is clamped and the
 * download is only called done once the bytes are on disk, so "100%" and "the
 * installer is opening" are the same moment rather than two seconds apart.
 */
fun progressPercent(read: Long, total: Long): Int =
    (progressFraction(read, total) * 100f).toInt().coerceIn(0, 100)

/**
 * What the file is called on disk.
 *
 * The release's own name is used when it is one -- `ikna-v0.4.0-press.apk` in the
 * installer's title is worth more than `update.apk` -- but it comes from the
 * network, so it is stripped to letters, digits, dots, dashes and underscores.
 * Nothing that could climb out of the folder survives that, and anything that
 * does not end up looking like an APK is named after the tag instead.
 */
fun apkFileName(tag: String, url: String): String {
    val plain = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val safe = plain.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
    if (safe.length > 4 && !safe.startsWith(".") && safe.endsWith(".apk", ignoreCase = true)) {
        return safe
    }
    val fromTag = tag
        .filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        .trim('.', '-', '_')
    // A tag of nothing but dots and dashes is not a name. It has to carry at
    // least one letter or digit, or the fixed name is used instead.
    return if (fromTag.none { it.isLetterOrDigit() }) "ikna-update.apk" else "ikna-$fromTag.apk"
}

/**
 * The download itself.
 *
 * One GET, to the URL the release listed, with the same shape of caution as the
 * check: a timeout on both ends, a ceiling on the size, and every failure
 * arriving as null rather than as an exception the window would have to explain.
 */
class UpdateDownload(private val context: Context) {

    /**
     * Writes [release]'s APK into the cache and returns it, or null.
     *
     * [onProgress] is called with the bytes so far and the total, once per whole
     * percent -- often enough that the band moves, rarely enough that it is not
     * the reason the download is slow. The total is the length the server
     * declares, falling back to the size the release page listed.
     */
    suspend fun fetch(
        release: UpdateRelease,
        onProgress: (read: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val name = apkFileName(release.tag, release.apkUrl)
        val dir = File(context.cacheDir, UPDATE_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return@withContext null
        // One file at a time. A previous attempt is never resumed and never
        // trusted, and a version nobody installed is not worth the megabytes.
        dir.listFiles()?.forEach { it.delete() }

        val target = File(dir, name)
        val partial = File(dir, "$name.part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "ikna")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val declared = connection.contentLengthLong
            val total = if (declared > 0L) declared else release.sizeBytes
            if (total > MAX_APK_BYTES) return@withContext null

            var read = 0L
            var shown = -1
            onProgress(0L, total)
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        // Cancelling the window has to stop the socket, not just
                        // stop looking at it.
                        if (!isActive) return@withContext null
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (read > MAX_APK_BYTES) return@withContext null
                        val percent = progressPercent(read, total)
                        if (percent != shown) {
                            shown = percent
                            onProgress(read, total)
                        }
                    }
                    output.flush()
                }
            }

            // A connection that dropped politely leaves a shorter file, not an
            // error. Handing that to the installer produces "app not installed",
            // which is the one message that makes people stop updating.
            if (read <= 0L) return@withContext null
            if (total > 0L && read < total) return@withContext null
            if (!partial.renameTo(target)) return@withContext null
            onProgress(read, total)
            target
        } catch (cancelled: CancellationException) {
            partial.delete()
            target.delete()
            throw cancelled
        } catch (failed: Exception) {
            partial.delete()
            target.delete()
            null
        } finally {
            connection?.disconnect()
            // Whatever is left is either the finished file or nothing at all.
            if (partial.exists()) partial.delete()
        }
    }
}

/**
 * Whether the platform will let this app ask to install a package.
 *
 * Installing from outside a store is a permission the user grants per app, on a
 * settings screen, and it cannot be requested with a dialog. So it is asked
 * about only at the moment it is needed -- with the file already downloaded, in
 * a window that says what it is for -- and never on first launch, where it would
 * read as an app asking to install software for no stated reason.
 */
fun canInstallPackages(context: Context): Boolean =
    runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

/** The one settings screen that grants it, opened straight at this app. */
fun openInstallPermission(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Hands the downloaded file to the system installer.
 *
 * Through the file provider that already exists for sharing a deck, so nothing
 * is made world-readable and the grant lasts for the one intent. The installer
 * takes it from there: it names the app, it says it is an update, it checks the
 * signature, and it is the thing that says yes -- not this code.
 */
fun installApk(context: Context, file: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)
