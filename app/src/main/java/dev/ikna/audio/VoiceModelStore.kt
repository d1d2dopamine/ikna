package dev.ikna.audio

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/*
 * The one voice model the app keeps, and the copy that puts it there.
 *
 * The app has no network permission and never will, so a model cannot be
 * downloaded here; it arrives from the file picker. It is copied into the app's
 * own storage rather than read where it lies, because espeak-ng opens its data
 * by path through C, and a picked folder is a content:// document, not a path.
 *
 * One model at a time, on purpose. Two would mean a screen for choosing between
 * them, a rule for what happens when both claim the same language, and twice the
 * disk; and nobody carrying a phone wants any of the three.
 */

/** A model that is on disk and ready to be pointed at. */
data class VoiceModelInstall(
    val kind: VoiceModelKind,
    val name: String,
    val lang: String?,
    val model: String,
    val speaker: Int,
    val bytes: Long,
    val dir: File,
) {
    /**
     * Identifies this model in the audio cache. The speaker number is part of it:
     * changing which of Kokoro's voices speaks must not keep playing yesterday's
     * rendering back from disk.
     */
    val id: String get() = name + "|" + model + "|" + speaker

    val file: File get() = File(dir, model)
}

/** What came of an attempt to add one. */
sealed interface VoiceModelResult {
    data class Installed(val install: VoiceModelInstall) : VoiceModelResult

    /** The folder is not usable, and [problem] says why in a way the screen can explain. */
    data class Refused(val problem: VoiceModelProblem) : VoiceModelResult

    /** The copy itself failed: no room left, a document that vanished, a denied permission. */
    data class Failed(val message: String?) : VoiceModelResult
}

class VoiceModelStore(context: Context) {

    private val app = context.applicationContext

    private val root: File get() = File(app.filesDir, DIR)
    private val staging: File get() = File(app.filesDir, DIR + ".part")
    private val manifest: File get() = File(root, MANIFEST)

    /** The installed model, or null. Cheap: reads one small text file. */
    fun installed(): VoiceModelInstall? = runCatching {
        if (!manifest.isFile) return@runCatching null
        val map = manifest.readLines()
            .mapNotNull { line ->
                val at = line.indexOf('=')
                if (at <= 0) null
                else line.substring(0, at).trim() to line.substring(at + 1).trim()
            }
            .toMap()

        val kind = VoiceModelKind.entries.firstOrNull { it.name == map["kind"] }
            ?: return@runCatching null
        val model = map["model"].orEmpty()
        if (model.isEmpty() || !File(root, model).isFile) return@runCatching null

        VoiceModelInstall(
            kind = kind,
            name = map["name"].orEmpty().ifEmpty { model },
            lang = map["lang"]?.takeIf { it.isNotEmpty() },
            model = model,
            speaker = map["speaker"]?.toIntOrNull() ?: 0,
            bytes = map["bytes"]?.toLongOrNull() ?: 0L,
            dir = root,
        )
    }.getOrNull()

    /**
     * Looks at a picked folder without copying anything, so the screen can say
     * what it is -- and what is wrong with it -- before the user commits to
     * minutes of copying.
     */
    suspend fun preview(tree: Uri): VoiceModelReport = withContext(Dispatchers.IO) {
        val doc = folderOf(tree)
            ?: return@withContext VoiceModelReport(problem = VoiceModelProblem.NOT_A_MODEL)
        inspect(doc)
    }

    /**
     * Copies a picked folder in, replacing whatever was there.
     *
     * Copied into a staging directory and swapped at the end: a copy interrupted
     * by a dead battery must not leave half a model looking installed. The old
     * one is only dropped once the new one is whole.
     *
     * @param onProgress called with the number of files copied so far, for a
     *   screen that would otherwise look frozen for a minute.
     */
    suspend fun install(
        tree: Uri,
        lang: String? = null,
        onProgress: (Int) -> Unit = {},
    ): VoiceModelResult = withContext(Dispatchers.IO) {
        val doc = folderOf(tree) ?: return@withContext VoiceModelResult.Failed(null)

        val report = inspect(doc)
        val model = report.model
        val kind = report.kind
        if (!report.usable || model == null || kind == null) {
            return@withContext VoiceModelResult.Refused(
                report.problem ?: VoiceModelProblem.NOT_A_MODEL
            )
        }

        runCatching {
            staging.deleteRecursively()
            staging.mkdirs()
            var copied = 0
            copyTree(doc, staging) {
                copied += 1
                onProgress(copied)
            }

            root.deleteRecursively()
            if (!staging.renameTo(root)) error("could not move the model into place")

            val name = doc.name.orEmpty().ifEmpty { model.substringBeforeLast('.') }
            val install = VoiceModelInstall(
                kind = kind,
                name = name,
                lang = (lang ?: report.lang)?.lowercase(Locale.ROOT),
                model = model,
                speaker = 0,
                bytes = sizeOf(root),
                dir = root,
            )
            write(install)
            VoiceModelResult.Installed(install)
        }.getOrElse { failure ->
            runCatching { staging.deleteRecursively() }
            VoiceModelResult.Failed(failure.message)
        }
    }

    /** Which language the model is used for. Asked for when the name did not say. */
    fun setLanguage(lang: String?) {
        val current = installed() ?: return
        write(current.copy(lang = lang?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }))
    }

    /** Which of the model's own voices speaks, for the ones that have several. */
    fun setSpeaker(speaker: Int) {
        val current = installed() ?: return
        write(current.copy(speaker = speaker.coerceAtLeast(0)))
    }

    /** Throws the model away. The app falls back to the phone's own voice. */
    fun remove() {
        runCatching { root.deleteRecursively() }
        runCatching { staging.deleteRecursively() }
    }

    // ---- internals ---------------------------------------------------------

    /**
     * The folder that actually holds the model.
     *
     * Picking the folder an archive was unpacked into instead of the folder
     * inside it is the commonest mistake there is, so one level of that is
     * followed silently rather than refused.
     */
    private fun folderOf(tree: Uri): DocumentFile? {
        val doc = runCatching { DocumentFile.fromTreeUri(app, tree) }.getOrNull() ?: return null
        if (!doc.isDirectory) return null
        if (inspect(doc).problem != VoiceModelProblem.NESTED) return doc
        return runCatching { doc.listFiles() }.getOrNull()
            ?.firstOrNull { it.isDirectory }
            ?: doc
    }

    private fun inspect(doc: DocumentFile): VoiceModelReport {
        val children = runCatching { doc.listFiles() }.getOrNull().orEmpty()
        val entries = children.mapNotNull { child ->
            val name = child.name ?: return@mapNotNull null
            VoiceEntry(name, child.isDirectory, if (child.isDirectory) 0L else child.length())
        }
        return VoiceModelLayout.inspect(doc.name.orEmpty(), entries)
    }

    private fun copyTree(doc: DocumentFile, dest: File, onFile: () -> Unit) {
        dest.mkdirs()
        val children = runCatching { doc.listFiles() }.getOrNull().orEmpty()
        for (child in children) {
            val name = child.name ?: continue
            // A document is trusted for its bytes, never for its name: one ".."
            // in there would write outside the app's own storage.
            if (name == "." || name == ".." || name.contains('/')) continue
            val target = File(dest, name)
            if (child.isDirectory) {
                copyTree(child, target, onFile)
            } else {
                app.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("could not read " + name)
                onFile()
            }
        }
    }

    private fun write(install: VoiceModelInstall) {
        runCatching {
            root.mkdirs()
            manifest.writeText(
                buildString {
                    append("kind=").append(install.kind.name).append('\n')
                    append("name=").append(install.name).append('\n')
                    append("lang=").append(install.lang.orEmpty()).append('\n')
                    append("model=").append(install.model).append('\n')
                    append("speaker=").append(install.speaker).append('\n')
                    append("bytes=").append(install.bytes).append('\n')
                }
            )
        }
    }

    private fun sizeOf(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            .getOrDefault(0L)

    private companion object {
        const val DIR = "voice-model"
        const val MANIFEST = "ikna-model.txt"
    }
}
