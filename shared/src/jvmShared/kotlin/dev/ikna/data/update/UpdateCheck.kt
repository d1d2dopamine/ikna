package dev.ikna.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    private fun parse(json: String): UpdateRelease? {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name").trim()
        if (tag.isEmpty() || !isNewer(installedVersion, tag)) return null
        val array = root.optJSONArray("assets") ?: return null
        val assets = ArrayList<UpdateAsset>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("browser_download_url")
            if (url.isEmpty()) continue
            assets.add(
                UpdateAsset(
                    name = item.optString("name"),
                    url = url,
                    sizeBytes = item.optLong("size")
                )
            )
        }
        val asset = pickAsset(assets, has64Bit) ?: return null
        // A release with no notes is still an update; a release with no file is
        // not, which is why the asset is what decides above.
        val name = root.optString("name").trim()
        return UpdateRelease(
            version = versionLabel(tag, name),
            tag = tag,
            notes = tidyNotes(root.optString("body")),
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
    }
}
