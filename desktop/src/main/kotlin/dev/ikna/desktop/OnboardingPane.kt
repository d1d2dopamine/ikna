package dev.ikna.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.ui.onboarding.FirstStudyDestination
import dev.ikna.ui.text.S
import kotlinx.coroutines.CancellationException
import dev.ikna.ui.onboarding.IknaOnboardingTitle
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaScrollableColumn
import dev.ikna.ui.theme.IknaWideButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One desktop introduction slide, named by the same catalogue keys as Android. */
private data class DesktopOnboardingSlide(
    val titleKey: String,
    val bodyKey: String,
    val demo: Boolean = false
)

private val DESKTOP_ONBOARDING_SLIDES = listOf(
    DesktopOnboardingSlide("onb.001", "onb.002"),
    DesktopOnboardingSlide("onb.003", "onb.004"),
    DesktopOnboardingSlide("onb.005", "onb.006", demo = true),
    DesktopOnboardingSlide("onb.011", "onb.012")
)

/**
 * The phone's four-step first launch, adapted only in width for a window.
 *
 * It is selected from the same persisted onboardingDone flag, so a fresh
 * desktop install and a completed full wipe both arrive here exactly once.
 */
@Composable
fun DesktopOnboardingPane(
    container: DesktopContainer,
    onDone: (FirstStudyDestination) -> Unit = {}
) {
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(FirstStudyDestination.SESSION) }
    val scope = rememberCoroutineScope()
    val slide = DESKTOP_ONBOARDING_SLIDES[step]

    fun finish(selected: FirstStudyDestination) {
        if (busy) return
        destination = selected
        busy = true
        failed = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) { container.completeOnboarding() }
                onDone(destination)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logLine("onboarding failed: " + error)
                failed = true
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        IknaScrollableColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(28.dp),
            scrollKey = step,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.weight(1f))

            IknaOnboardingTitle(
                titleKey = slide.titleKey,
                branded = step == 0
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = S.t(slide.bodyKey),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (slide.demo) {
                Spacer(Modifier.height(24.dp))
                GestureDemo()
            }

            if (step == DESKTOP_ONBOARDING_SLIDES.lastIndex) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = S.t("onb.014"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DESKTOP_ONBOARDING_SLIDES.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (index == step) 16.dp else 8.dp,
                                height = 8.dp
                            )
                            .background(
                                if (index == step) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (failed) {
                Text(
                    text = S.t("onb.013"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }

            IknaWideButton(
                label = when {
                    busy -> S.t("onb.007")
                    failed -> S.t("load.002")
                    step < DESKTOP_ONBOARDING_SLIDES.lastIndex -> S.t("onb.008")
                    else -> S.t("onb.009")
                },
                filled = true,
                enabled = !busy,
                height = 56.dp,
                onClick = {
                    if (step < DESKTOP_ONBOARDING_SLIDES.lastIndex) {
                        step++
                    } else {
                        finish(if (failed) destination else FirstStudyDestination.SESSION)
                    }
                }
            )

            if (step < DESKTOP_ONBOARDING_SLIDES.lastIndex) {
                IknaTextButton(
                    label = S.t("onb.010"),
                    onClick = { step = DESKTOP_ONBOARDING_SLIDES.lastIndex },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                IknaTextButton(
                    label = S.t("onb.015"),
                    enabled = !busy,
                    onClick = { finish(FirstStudyDestination.CATALOGUE) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The same static answer explanation the phone shows beside the error text. */
@Composable
private fun GestureDemo() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = S.t("card.003"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.5f)
                .padding(start = 20.dp, bottom = 12.dp)
        )
        Text(
            text = S.t("card.004"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.5f)
                .padding(end = 20.dp, bottom = 12.dp)
        )
    }
}
