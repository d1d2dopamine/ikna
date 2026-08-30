package dev.ikna.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks the releases page whether there is a newer build. Nothing else.
 *
 * This is the only socket in the app, and the shape of it is the point:
 *
 * - One GET, no body, no cookie, no identifier. The only thing the server
 *   learns that it would not learn from a browser is that some ikna somewhere
 *   is on this version, and it learns that because the version is in the user
 *   agent -- which is there so a broken release can be recognised in the logs,
 *   not so anybody can be counted.
 * - Short timeouts. A check is never worth a spinner: if the network is slow
 *   the app has already been used and closed by the time it would answer.
 * - A cap on the reply. A JSON parser handed an endless stream is a way to run
 *   a phone out of memory, and this one does not get the chance.
 * - Every failure is null. No message, no retry, no red mark. The app worked
 *   without this for four versions; a check that failed is not an event.
 */
class UpdateCheck(
    private val installedVersion: String,
    private val has64Bit: Boolean
) {

    suspend fun latest(): UpdateRelease? = withContext(Dispatchers.IO) {
        val json = runCatching { fetch() }.getOrNull() ?: return@withContext null
        runCatching { parse(json) }.getOrNull()
    }

    private fun fetch(): String? {
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ikna/" + installedVersion)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val buffer = CharArray(8 * 1024)
            val text = StringBuilder()
            connection.inputStream.reader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    text.append(buffer, 0, read)
                    if (text.length > MAX_REPLY) return null
                }
            }
            return text.toString()
        } finally {
            connection.disconnect()
        }
    }

    // kotlinx.serialization rather than org.json.
    //
    // org.json is not a library this build depends on, it is a class the
    // Android platform happens to carry in android.jar. This file lives in
    // jvmShared and is therefore compiled twice -- once for Android, where the
    // class is free, and once for the desktop JVM, where it does not exist at
    // all. The Windows build failed on the import and nowhere else.
    //
    // kotlinx-serialization-json is already an api dependency of this source
    // set and is what the catalogue is read with, so this adds nothing to
    // either application. The three readers at the bottom of this file keep the
    // behaviour of optString/optBoolean/optLong exactly: a field that is
    // missing, null or of the wrong type is a default, never an exception. The
    // Android build therefore treats a reply the same way it did before.
    private fun parse(json: String): UpdateRelease? {
        val root = JSON.parseToJsonElement(json) as? JsonObject ?: return null
        if (root.flag("draft") || root.flag("prerelease")) return null
        val tag = root.text("tag_name").trim()
        if (tag.isEmpty() || !isNewer(installedVersion, tag)) return null
        val array = root["assets"] as? JsonArray ?: return null
        val assets = ArrayList<UpdateAsset>(array.size)
        for (element in array) {
            val item = element as? JsonObject ?: continue
            val url = item.text("browser_download_url")
            if (url.isEmpty()) continue
            assets.add(
                UpdateAsset(
                    name = item.text("name"),
                    url = url,
                    sizeBytes = item.number("size")
                )
            )
        }
        val asset = pickAsset(assets, has64Bit) ?: return null
        // A release with no notes is still an update; a release with no file is
        // not, which is why the asset is what decides above.
        val name = root.text("name").trim()
        return UpdateRelease(
            version = versionLabel(tag, name),
            tag = tag,
            notes = tidyNotes(root.text("body")),
            apkUrl = asset.url,
            sizeBytes = asset.sizeBytes
        )
    }

    private fun versionLabel(tag: String, name: String): String {
        // The release is called "ikna 0.5.0 press" and the tag is "v0.5.0-press".
        // Neither is what belongs next to an arrow, so the numbers and the word
        // are recovered from whichever of the two has them.
        val source = if (versionNumbers(name) != null) name else tag
        return source
            .removePrefix("ikna ")
            .trimStart('v', 'V')
            .replace('-', ' ')
            .trim()
    }

    companion object {
        const val RELEASES_PAGE: String = "https://github.com/d1d2dopamine/ikna/releases"
        private const val LATEST_URL =
            "https://api.github.com/repos/d1d2dopamine/ikna/releases/latest"
        private const val TIMEOUT_MS = 8_000
        private const val MAX_REPLY = 512 * 1024

        /** Once a day. A check on every launch is a check nobody asked for. */
        const val CHECK_EVERY_MS: Long = 24L * 60L * 60L * 1000L

        // The reply carries far more than the five fields read above, and a
        // field the API adds later must not turn a working check into a
        // failing one.
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The value of [key] as text, or "" when it is absent, null, or not a scalar.
 *
 * This is optString, written out: the caller decides what an empty answer
 * means, and no reply from the network can throw its way out of here.
 */
private fun JsonObject.text(key: String): String {
    val value = this[key]
    if (value !is JsonPrimitive || value is JsonNull) return ""
    return value.content
}

/** True only where [key] is present and says so; anything else is false. */
private fun JsonObject.flag(key: String): Boolean =
    text(key).equals("true", ignoreCase = true)

/** The value of [key] as a whole number, or 0 where it is not one. */
private fun JsonObject.number(key: String): Long = text(key).toLongOrNull() ?: 0L
