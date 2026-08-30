package dev.ikna.data.pack

import android.content.Context
import dev.ikna.data.db.ChunkDao
import dev.ikna.platform.AndroidAssets

/** PackLoader(context, dao), still -- see SettingsStore.android.kt. */
fun PackLoader(context: Context, chunkDao: ChunkDao): PackLoader =
    PackLoader(AndroidAssets(context), chunkDao)
