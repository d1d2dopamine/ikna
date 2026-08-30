package dev.ikna.platform

import java.io.InputStream

/**
 * Read-only bundled content: the decks in packs/, and governor.json.
 *
 * Android calls this AssetManager and reaches it through a Context; on the
 * desktop the same files are resources on the classpath. One function is the
 * whole difference, so it is one function.
 */
fun interface Assets {
    fun open(path: String): InputStream
}
