package dev.ikna.platform

import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The same asset folder, copied onto the classpath under /assets by the desktop
 * module's processResources step. The decks are not duplicated in the
 * repository: both applications read app/src/main/assets.
 */
object ClasspathAssets : Assets {

    private object ResourceAnchor

    override fun open(path: String): InputStream =
        ResourceAnchor.javaClass.getResourceAsStream("/assets/" + path)
            ?: throw FileNotFoundException("assets/" + path)
}
