package dev.ikna.platform

import android.content.Context
import java.io.InputStream

class AndroidAssets(context: Context) : Assets {
    private val assets = context.applicationContext.assets
    override fun open(path: String): InputStream = assets.open(path)
}
