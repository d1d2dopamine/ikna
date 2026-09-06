package dev.ikna.ui.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import dev.ikna.domain.session.ReviewSignalTracker
import dev.ikna.domain.session.TimingDiscardReason

/**
 * Sticky invalidation of the stopwatch, not of the answer. Android callbacks
 * complement the shared WindowInfo observer: lifecycle catches backgrounding,
 * and screen-off is invalid even if the device never delivers a focus change.
 * No service, wake lock, permission, background task or network request is added.
 */
@Composable
internal fun ObserveReviewInterruptions(signals: ReviewSignalTracker) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view, signals) {
        val owner = view.findViewTreeLifecycleOwner()
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                signals.interrupt(TimingDiscardReason.APP_BACKGROUND)
            }
        }
        owner?.lifecycle?.addObserver(lifecycleObserver)
        if (owner != null && !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            signals.interrupt(TimingDiscardReason.APP_BACKGROUND)
        }

        val tree = view.viewTreeObserver
        val focusObserver = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) signals.interrupt(TimingDiscardReason.FOCUS_LOST)
        }
        tree.addOnWindowFocusChangeListener(focusObserver)
        if (!view.hasWindowFocus()) signals.interrupt(TimingDiscardReason.FOCUS_LOST)

        val screenObserver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    signals.interrupt(TimingDiscardReason.SCREEN_OFF)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            screenObserver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            owner?.lifecycle?.removeObserver(lifecycleObserver)
            if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focusObserver)
            context.unregisterReceiver(screenObserver)
            signals.interrupt(TimingDiscardReason.PRESENTATION_INTERRUPTED)
        }
    }
}
