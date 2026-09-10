package dev.ikna.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import dev.ikna.ui.text.S
import dev.ikna.ui.onboarding.IknaOnboardingTitle
import dev.ikna.ui.theme.IknaTextButton
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
    onDone: () -> Unit = {}
) {
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val slide = DESKTOP_ONBOARDING_SLIDES[step]

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(28.dp),
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

            IknaWideButton(
                label = when {
                    busy -> S.t("onb.007")
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
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { container.completeOnboarding() }
                            }
                            result.onSuccess {
                                busy = false
                                onDone()
                            }.onFailure { error ->
                                logLine("onboarding failed: " + error)
                                busy = false
                            }
                        }
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
                Spacer(Modifier.height(44.dp))
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
            .height(132.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = S.t("card.003"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 12.dp)
        )
        Text(
            text = S.t("card.004"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 12.dp)
        )
    }
}
