package dev.ikna.domain.governor

import android.content.Context
import dev.ikna.platform.AndroidAssets

fun loadGovernorConfig(context: Context): GovernorConfig =
    GovernorConfig.load(AndroidAssets(context))
