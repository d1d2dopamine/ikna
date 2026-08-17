package dev.ikna.audio

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/*
 * The voice models the app keeps, and the two ways they get there.
 *
 * The app has no network permission and never will, so a model cannot be
 * downloaded here; it arrives from the file picker, either as a folder or as the
 * `.tar.bz2` it was published as. It is copied into the app's own storage rather
 * than read where it lies, because espeak-ng opens its data by path through C,
 * and a picked document is a content:// handle, not a path.
 *
 * Until 0.5.0 there was one slot. The reasoning was that two models would mean a
 * screen for choosing between them, a rule for what happens when both claim the
 * same language, and twice the disk. All three turned out to be worth it the
 * first time somebody studying two languages had to destroy their Russian voice
 * to hear an English one -- and then copy sixty megabytes back to undo it.
 *
 * So: a folder per model, each with its own manifest, and one of them switched on
 * per language. The rule about a tie is written once, in demoteOthers, and it is
 * the blunt one: the model switched on most recently wins its language and the
 * previous holder is switched off. A coin toss between two voices is not
 * something an app should do quietly.
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
    val enabled: Boolean = true,
    /**
     * How many voices the net holds, as the runtime reported it, or 0 before it
     * has ever been loaded.
     *
     * Kokoro addresses its voices by number and by nothing else, and a number
     * past the last one does not come back as an error: sherpa-onnx checks the
     * index down in C++ and ends the process, which is a crash with no Kotlin
     * frame anywhere to catch. So the count is written down the first time a net
     * is read in, and it is the only thing allowed to bound the buttons that
     * change the number. Zero means "never asked", and zero permits voice 0
     * alone -- the one voice every model has.
     */
    val speakers: Int = 0,
    /**
     * This model's own speed, as a percent of its natural pace.
     *
     * Per model rather than one number for all of them: a Piper voice recorded
     * slowly and a Kokoro voice that already hurries are not the same voice at
     * 100%, and a single setting for both is a compromise that suits neither.
     * The phone's own engine keeps its own pair in settings, where pitch works
     * too -- here there is no pitch to keep, because a neural voice has one, its
     * own.
     */
    val rate: Int = DEFAULT_RATE,
) {
    /** The folder name. Stable, unique, and what the screen passes back to change things. */
    val slug: String get() = dir.name

    /**
     * Identifies this model in the audio cache. The voice number and the speed
     * are part of it: changing which of Kokoro's voices speaks, or how fast it
     * speaks, must not keep playing yesterday's rendering back from disk. So is
     * the folder, so that two models never share a cache entry.
     */
    val id: String get() = slug + "|" + model + "|" + speaker + "|" + rate

    /**
     * Identifies the net in memory, which is a different question from [id]: the
     * voice number and the speed are arguments to one rendering, not properties
     * of the net. Keying the resident engine by [id] meant every tap on "+"
     * released a hundred megabytes of model and read it straight back in --
     * slow, and the window in which a rendering already running was holding
     * memory that had just been freed.
     */
    val engineKey: String get() = slug + "|" + model

    /**
     * The voice number that is safe to hand to the runtime: the chosen one when
     * the count is known and it fits, and voice 0 in every other case.
     */
    val voice: Int get() = if (speakers <= 0) 0 else speaker.coerceIn(0, speakers - 1)

    /** This model's speed as the runtime wants it, inside the range it accepts. */
    val speed: Float get() = (rate / 100f).coerceIn(MIN_RATE / 100f, MAX_RATE / 100f)

    val file: File get() = File(dir, model)
}

/**
 * The speed range of a model of one's own, in percent.
 *
 * The same range the phone's own voice offers in settings, so the two controls
 * behave alike; the step is the same too. Past this speech stops being speech.
 */
const val MIN_RATE = 50
const val MAX_RATE = 150
const val RATE_STEP = 10
const val DEFAULT_RATE = 100

/** What came of an attempt to add one. */
sealed interface VoiceModelResult {
    data class Installed(val install: VoiceModelInstall) : VoiceModelResult

    /** Not usable, and [problem] says why in a way the screen can explain. */
    data class Refused(val problem: VoiceModelProblem) : VoiceModelResult

    /** The copy itself failed: a document that vanished, a denied permission. */
    data class Failed(val message: String?) : VoiceModelResult
}

class VoiceModelStore(context: Context) {

    private val app = context.applicationContext

    private val root: File get() = File(app.filesDir, DIR)
    private val staging: File get() = File(app.filesDir, DIR + ".part")

    /** Where the single model lived before 0.5.0. Moved on first read, then gone. */
    private val legacy: File get() = File(app.filesDir, LEGACY_DIR)

    /**
     * Every installed model. Cheap: one small text file per folder, no weights
     * touched. Sorted by name so the screen does not reshuffle itself between
     * one visit and the next.
     */
    fun installed(): List<VoiceModelInstall> = runCatching {
        migrate()
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { read(it) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }.getOrDefault(emptyList())

    /** The models allowed to speak. */
    fun enabled(): List<VoiceModelInstall> = installed().filter { it.enabled }

    /**
     * Which model reads this language, or none.
     *
     * A model that named its language is preferred over one that named none, so
     * installing a multi-language release does not quietly take a language away
     * from the voice that was chosen for it. A model that named none is offered
     * for anything, because refusing every deck is the one certainly wrong answer.
     */
    fun forLanguage(lang: String): VoiceModelInstall? {
        val on = enabled()
        return on.firstOrNull { it.lang != null && speaks(it, lang) }
            ?: on.firstOrNull { it.lang == null }
    }

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
     * Copies a picked folder in as a new model, keeping the ones already there.
     *
     * Copied into a staging directory and moved into place at the end: a copy
     * interrupted by a dead battery must not leave half a model looking
     * installed.
     *
     * @param onProgress called with the bytes copied so far out of the size of
     *   the folder, and not merely with the files finished: one file of a
     *   Kokoro release is the entire copy, and a count of finished files sits
     *   at zero throughout it.
     */
    suspend fun install(
        tree: Uri,
        lang: String? = null,
        onProgress: (VoiceInstallProgress) -> Unit = {},
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

        val name = doc.name.orEmpty().ifEmpty { model.substringBeforeLast('.') }

        runCatching {
            migrate()
            root.mkdirs()
            staging.deleteRecursively()
            staging.mkdirs()
            val total = sizeOfDoc(doc)
            var files = 0
            var copied = 0L
            copyTree(doc, staging) { delta, finished ->
                copied += delta
                if (finished) files += 1
                onProgress(VoiceInstallProgress(files, copied, total))
            }

            val home = File(root, slugFor(name))
            home.deleteRecursively()
            if (!staging.renameTo(home)) error("could not move the model into place")

            settle(kind, name, lang ?: report.lang, model, home)
        }.getOrElse { failure ->
            runCatching { staging.deleteRecursively() }
            VoiceModelResult.Failed(failure.message)
        }
    }

    /**
     * Installs a model straight from the archive it was downloaded as.
     *
     * Every model on the sherpa-onnx page is a `.tar.bz2`, and until 0.5.0 the app
     * could only take an unpacked folder -- which meant a third-party file
     * manager, two extractions, and instructions longer than the screen they were
     * about. The bytes were always the same bytes; the only reason they were
     * somebody else's problem is that Android has no bzip2.
     *
     * The same staging discipline as [install]: unpacked to one side, examined,
     * moved into place only once it turns out to be a model. An archive holding a
     * folder rather than the files themselves is followed one level in, because
     * that is how nearly all of them are packed.
     */
    suspend fun installArchive(
        source: Uri,
        lang: String? = null,
        onProgress: (VoiceInstallProgress) -> Unit = {},
    ): VoiceModelResult = withContext(Dispatchers.IO) {
        val doc = runCatching { DocumentFile.fromSingleUri(app, source) }.getOrNull()
        val archiveName = doc?.name.orEmpty().ifEmpty { "model.tar.bz2" }
        val packed = doc?.length() ?: 0L

        if (!VoiceArchive.looksLikeArchive(archiveName)) {
            return@withContext VoiceModelResult.Refused(VoiceModelProblem.NOT_AN_ARCHIVE)
        }

        // A speech model compresses to roughly a third of its size, so unpacking
        // needs several times the download. Checked up front: filling the phone
        // and failing on the last file of sixty is a worse way to find out.
        val room = root.parentFile?.usableSpace ?: Long.MAX_VALUE
        if (packed > 0L && room < packed * 4) {
            return@withContext VoiceModelResult.Refused(VoiceModelProblem.NO_SPACE)
        }

        runCatching {
            migrate()
            root.mkdirs()
            staging.deleteRecursively()
            staging.mkdirs()

            var files = 0
            app.contentResolver.openInputStream(source)?.use { input ->
                files = VoiceArchive.unpack(
                    source = input,
                    dest = staging,
                    compressed = VoiceArchive.isCompressed(archiveName),
                ) { done, read -> onProgress(VoiceInstallProgress(done, read, packed)) }
            } ?: error("could not read " + archiveName)
            if (files == 0) error("the archive held no files")

            // The archive's own name stands in for a folder name: the language of
            // a model is written in it and nowhere else.
            val outer = VoiceArchive.folderName(archiveName)
            val candidates = mutableListOf(outer to staging)
            staging.listFiles().orEmpty()
                .filter { it.isDirectory }
                .sortedBy { it.name }
                .forEach { candidates.add(it.name to it) }

            val found = candidates.firstNotNullOfOrNull { (name, dir) ->
                val report = inspectDir(name, dir)
                if (report.usable) Triple(name, dir, report) else null
            }

            if (found == null) {
                val problem = inspectDir(outer, staging).problem
                    ?: VoiceModelProblem.NOT_A_MODEL
                staging.deleteRecursively()
                return@runCatching VoiceModelResult.Refused(problem)
            }

            val (name, dir, report) = found
            val model = report.model ?: error("no model file")
            val kind = report.kind ?: error("no model kind")

            val home = File(root, slugFor(name))
            home.deleteRecursively()
            if (!dir.renameTo(home)) error("could not move the model into place")
            staging.deleteRecursively()

            settle(kind, name, lang ?: report.lang, model, home)
        }.getOrElse { failure ->
            runCatching { staging.deleteRecursively() }
            VoiceModelResult.Failed(failure.message)
        }
    }

    /** Which language a model is used for. Asked for when its name did not say. */
    fun setLanguage(slug: String, lang: String?) {
        val current = find(slug) ?: return
        val next = current.copy(
            lang = lang?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        )
        write(next)
        if (next.enabled) demoteOthers(next)
    }

    /**
     * Which of a model's own voices speaks, for the ones that have several.
     *
     * Clamped to the voices the net actually has, and to voice 0 while that
     * number is unknown. Not tidiness: sherpa-onnx validates the voice number
     * inside C++, and its answer to one past the end is to end the process, so a
     * number nothing has confirmed never reaches it.
     */
    fun setSpeaker(slug: String, speaker: Int) {
        val current = find(slug) ?: return
        val top = if (current.speakers > 0) current.speakers - 1 else 0
        write(current.copy(speaker = speaker.coerceIn(0, top)))
    }

    /**
     * Writes down how many voices a net turned out to have.
     *
     * Called once, by whoever loaded it, because the runtime is the only honest
     * source there is for this: the folder name does not say, the file size does
     * not say, and guessing is what let the voice number kill the app. Kept in
     * the manifest so the screen can bound its own buttons without loading
     * anything, and a number chosen by an older version is pulled back into
     * range here, once, rather than crashing the next time it is used.
     */
    fun setSpeakerCount(slug: String, speakers: Int) {
        if (speakers <= 0) return
        val current = find(slug) ?: return
        if (current.speakers == speakers && current.speaker < speakers) return
        write(
            current.copy(
                speakers = speakers,
                speaker = current.speaker.coerceIn(0, speakers - 1),
            )
        )
    }

    /**
     * How fast this model speaks, in percent of its own natural pace.
     *
     * Stored beside the model rather than in settings, because it belongs to this
     * voice the way its language does. The phone's own engine keeps its pair of
     * numbers in settings, where pitch means something as well.
     */
    fun setRate(slug: String, rate: Int) {
        val current = find(slug) ?: return
        write(current.copy(rate = rate.coerceIn(MIN_RATE, MAX_RATE)))
    }

    /**
     * Switches a model on or off without deleting it.
     *
     * "This voice is worse than my phone's" should cost one tap to act on and one
     * to take back, not sixty megabytes of copying.
     */
    fun setEnabled(slug: String, on: Boolean) {
        val current = find(slug) ?: return
        val next = current.copy(enabled = on)
        write(next)
        if (on) demoteOthers(next)
    }

    /** Throws one model away. The others stay; the phone's own voice covers the gap. */
    fun remove(slug: String) {
        val current = find(slug) ?: return
        runCatching { current.dir.deleteRecursively() }
    }

    // ---- internals ---------------------------------------------------------

    /** Writes the manifest for a freshly moved folder and lets it win its language. */
    private fun settle(
        kind: VoiceModelKind,
        name: String,
        lang: String?,
        model: String,
        home: File,
    ): VoiceModelResult {
        val install = VoiceModelInstall(
            kind = kind,
            name = name,
            lang = lang?.lowercase(Locale.ROOT),
            model = model,
            speaker = 0,
            bytes = sizeOf(home),
            dir = home,
            enabled = true,
        )
        write(install)
        // A newly added model is the one the user just went to the trouble of
        // finding, so it wins its language outright.
        demoteOthers(install)
        return VoiceModelResult.Installed(install)
    }

    private fun find(slug: String): VoiceModelInstall? =
        installed().firstOrNull { it.slug == slug }

    /** Whether this model claims that language. A model that named none claims all. */
    private fun speaks(install: VoiceModelInstall, lang: String): Boolean {
        val declared = install.lang ?: return true
        // Decks made by the user are stored as "custom": no language was ever
        // declared for them, and a model that was deliberately installed is a
        // better answer than silence.
        if (lang.isEmpty() || lang.equals(UNKNOWN, ignoreCase = true)) return true
        return lang.take(2).equals(declared.take(2), ignoreCase = true)
    }

    /**
     * Switches off whatever else claims the winner's language.
     *
     * Two models with an equal claim to a deck would otherwise be decided by
     * folder order, which is a coin toss the user cannot see, let alone change.
     */
    private fun demoteOthers(winner: VoiceModelInstall) {
        for (other in installed()) {
            if (other.slug == winner.slug || !other.enabled) continue
            val clash = when {
                winner.lang == null || other.lang == null -> true
                else -> other.lang.take(2).equals(winner.lang.take(2), ignoreCase = true)
            }
            if (clash) write(other.copy(enabled = false))
        }
    }

    /**
     * Moves a pre-0.5.0 model into the new layout.
     *
     * Renamed, never re-copied: somebody with a sixty-megabyte Piper voice should
     * not spend a minute of I/O on an app update they did not ask for, and a
     * phone with 100 MB free should not need 160 to survive one.
     */
    private fun migrate() {
        if (!legacy.isDirectory) return
        runCatching {
            if (!File(legacy, MANIFEST).isFile) {
                legacy.deleteRecursively()
                return
            }
            root.mkdirs()
            val home = File(root, slugFor(read(legacy)?.name ?: "model"))
            home.deleteRecursively()
            if (!legacy.renameTo(home)) return
        }
    }

    /**
     * A folder name for a model name: readable, safe on any filesystem, unique.
     *
     * The name is what the sherpa-onnx release was called, and it is worth keeping
     * in the path -- a folder of hashes is impossible to make sense of from a file
     * manager, and somebody debugging a silent voice will be looking there.
     */
    private fun slugFor(name: String): String {
        val cleaned = name
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '-' }
            .joinToString("")
            .trim('-', '.')
            .take(60)
            .ifEmpty { "model" }
        if (!File(root, cleaned).exists()) return cleaned
        for (n in 2..99) {
            val candidate = cleaned + "-" + n
            if (!File(root, candidate).exists()) return candidate
        }
        return cleaned + "-" + System.currentTimeMillis()
    }

    /** Reads one model folder, or null when it holds no usable manifest. */
    private fun read(dir: File): VoiceModelInstall? = runCatching {
        val map = readMap(File(dir, MANIFEST)) ?: return@runCatching null
        val kind = VoiceModelKind.entries.firstOrNull { it.name == map["kind"] }
            ?: return@runCatching null
        val model = map["model"].orEmpty()
        if (model.isEmpty() || !File(dir, model).isFile) return@runCatching null

        VoiceModelInstall(
            kind = kind,
            name = map["name"].orEmpty().ifEmpty { model },
            lang = map["lang"]?.takeIf { it.isNotEmpty() },
            model = model,
            speaker = map["speaker"]?.toIntOrNull() ?: 0,
            bytes = map["bytes"]?.toLongOrNull() ?: 0L,
            dir = dir,
            // Neither line exists in a manifest written before this version, so
            // both fall back instead of refusing the folder: an unknown count
            // reads as "voice 0 until a load says otherwise", and an unknown
            // speed as the model's own pace.
            speakers = map["speakers"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            rate = map["rate"]?.toIntOrNull()?.coerceIn(MIN_RATE, MAX_RATE) ?: DEFAULT_RATE,
            // Anything but "no" is on, so a manifest written by 0.4.0 -- which had
            // no such line -- reads as switched on rather than as silence.
            enabled = map["enabled"] != "no",
        )
    }.getOrNull()

    private fun readMap(file: File): Map<String, String>? = runCatching {
        if (!file.isFile) return@runCatching null
        file.readLines()
            .mapNotNull { line ->
                val at = line.indexOf('=')
                if (at <= 0) null
                else line.substring(0, at).trim() to line.substring(at + 1).trim()
            }
            .toMap()
    }.getOrNull()

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

    /**
     * The same reading of a folder as [inspect], for one that is a real directory
     * rather than a pile of content:// documents.
     *
     * @param name what the folder should be judged by. An archive unpacked at its
     *   top level has no folder of its own, so its file name is passed instead.
     */
    private fun inspectDir(name: String, dir: File): VoiceModelReport {
        val entries = dir.listFiles().orEmpty().map { child ->
            VoiceEntry(
                child.name,
                child.isDirectory,
                if (child.isDirectory) 0L else child.length(),
            )
        }
        return VoiceModelLayout.inspect(name, entries)
    }

    /**
     * Copies a picked folder in, a megabyte at a time and a word after each one.
     *
     * @param onProgress bytes just written, and whether that finished a file.
     *   Both, because neither alone is enough: a Piper voice is many small files
     *   and a Kokoro release is one enormous one, and a screen has to keep moving
     *   through either.
     */
    private fun copyTree(doc: DocumentFile, dest: File, onProgress: (Long, Boolean) -> Unit) {
        dest.mkdirs()
        val children = runCatching { doc.listFiles() }.getOrNull().orEmpty()
        for (child in children) {
            val name = child.name ?: continue
            // A document is trusted for its bytes, never for its name: one ".."
            // in there would write outside the app's own storage.
            if (name == "." || name == ".." || name.contains('/')) continue
            val target = File(dest, name)
            if (child.isDirectory) {
                copyTree(child, target, onProgress)
            } else {
                app.contentResolver.openInputStream(child.uri)?.use { input ->
                    BufferedOutputStream(FileOutputStream(target), BUFFER).use { output ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            onProgress(read.toLong(), false)
                        }
                    }
                } ?: error("could not read " + name)
                onProgress(0L, true)
            }
        }
    }

    /**
     * What a picked folder weighs, so its copy can be shown as a fraction.
     *
     * One pass over the listing before anything is written. It costs a moment on
     * a folder of a dozen files, which is every model there is, and it buys the
     * difference between a percentage and a number nobody can judge.
     */
    private fun sizeOfDoc(doc: DocumentFile): Long = runCatching {
        var total = 0L
        val children = runCatching { doc.listFiles() }.getOrNull().orEmpty()
        for (child in children) {
            total += if (child.isDirectory) sizeOfDoc(child) else child.length()
        }
        total
    }.getOrDefault(0L)

    private fun write(install: VoiceModelInstall) {
        runCatching {
            install.dir.mkdirs()
            File(install.dir, MANIFEST).writeText(
                buildString {
                    append("kind=").append(install.kind.name).append('\n')
                    append("name=").append(install.name).append('\n')
                    append("lang=").append(install.lang.orEmpty()).append('\n')
                    append("model=").append(install.model).append('\n')
                    append("speaker=").append(install.speaker).append('\n')
                    append("speakers=").append(install.speakers).append('\n')
                    append("rate=").append(install.rate).append('\n')
                    append("bytes=").append(install.bytes).append('\n')
                    append("enabled=").append(if (install.enabled) "yes" else "no").append('\n')
                }
            )
        }
    }

    private fun sizeOf(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            .getOrDefault(0L)

    private companion object {
        /**
         * How much is copied at a time.
         *
         * The standard library's default is eight kilobytes, which is fine for
         * a settings file and absurd for a model: three hundred megabytes
         * through a content provider in eight-kilobyte pieces is most of the
         * wait people reported as the app hanging.
         */
        const val BUFFER = 1 shl 20

        const val DIR = "voice-models"
        const val LEGACY_DIR = "voice-model"
        const val MANIFEST = "ikna-model.txt"

        /** What the importer writes when a deck never said what language it is. */
        const val UNKNOWN = "custom"
    }
}
