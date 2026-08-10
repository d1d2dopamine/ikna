package dev.ikna.ui.debug

import androidx.compose.runtime.Composable
import dev.ikna.AppContainer

/**
 * The release build's half of the debug screen: nothing.
 *
 * There are two files named `DebugHooks.kt` in this project, one in
 * `src/debug/java` and one here in `src/release/java`. Only one of them is
 * compiled into any given build, which is what keeps the technical screen out
 * of the app people install — not a hidden button, and not a comment saying it
 * is for developers.
 *
 * A `BuildConfig.DEBUG` check would not have been enough. R8 is switched off in
 * both build types of this project (`isMinifyEnabled = false`), so nothing gets
 * stripped for being unreachable: a screen registered in the navigation graph
 * ships in the APK whether or not anything can open it, together with its
 * strings. Source sets decide what exists before the compiler starts.
 *
 * [available] is what the settings screen asks before drawing the way in, and it
 * is a `const`, so in a release build the branch is a constant `false`.
 */
object DebugHooks {
	const val available: Boolean = false

	@Composable
	@Suppress("UNUSED_PARAMETER")
	fun Screen(container: AppContainer, onBack: () -> Unit) {
		// Intentionally empty. The route stays registered so the navigation graph
		// is identical in both builds; nothing can reach it, because the entry
		// point in settings is behind `available`.
	}
}
