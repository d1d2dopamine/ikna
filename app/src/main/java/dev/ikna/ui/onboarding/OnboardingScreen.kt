package dev.ikna.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaAppearanceVariant
import dev.ikna.data.repo.prepareFirstSession
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaBrandPatternBackdrop
import dev.ikna.ui.theme.IknaScrollableColumn
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.LocalIknaAppearanceVariant
import dev.ikna.ui.theme.LocalIknaMotionEnabled
import dev.ikna.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Slides hold text keys, not text.
 *
 * The list lives at file scope, so it is built once when the class loads. If it
 * held finished strings, they would be the strings of whatever language was
 * active at that moment and the screen would keep them after a language switch.
 */
private data class Slide(
    val titleKey: String,
    val bodyKey: String
)

private val SLIDES = listOf(
    Slide("onb.001", "onb.002"),
    Slide("onb.003", "onb.004"),
    Slide("onb.005", "onb.006"),
    Slide("onb.011", "onb.012")
)

/**
 * Four screens, then the first card.
 *
 * The screens keep the author's own order: why ikna exists, how the finite
 * daily plan works, whether the app feels useful and what an error does, then
 * where progress lives and how feedback is welcomed. No configuration is
 * required before the first session.
 */
@Composable
fun OnboardingScreen(container: AppContainer, onDone: (FirstStudyDestination) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(FirstStudyDestination.SESSION) }
    val scope = rememberCoroutineScope()
    val slide = SLIDES[step]
    val motionEnabled = LocalIknaMotionEnabled.current
    val rounded = LocalIknaAppearanceVariant.current == IknaAppearanceVariant.ROUNDED
    val dotShape = RoundedCornerShape(if (rounded) 50 else 0)

    fun finish(selected: FirstStudyDestination) {
        if (busy) return
        destination = selected
        busy = true
        failed = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    prepareFirstSession(
                        installPacks = { container.packLoader.installBundledPacks() },
                        preparePlan = { container.learningRepository.ensureDailyPlan() },
                        markComplete = { container.settings.setOnboardingDone(true) }
                    )
                }
                onDone(destination)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        IknaBrandPatternBackdrop(intensity = 0.72f)
        IknaScrollableColumn(
            modifier = Modifier
                .fillMaxSize()
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

            if (step == SLIDES.lastIndex) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = S.t("onb.014"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SLIDES.indices.forEach { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == step) 22.dp else 8.dp,
                        animationSpec = if (motionEnabled) tween(Motion.controlChangeDurationMillis) else snap(),
                        label = "onb-step-dot"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(dotShape)
                            .background(
                                if (i == step) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                dotShape
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
                    step < SLIDES.lastIndex -> S.t("onb.008")
                    else -> S.t("onb.009")
                },
                filled = true,
                enabled = !busy,
                height = 56.dp,
                onClick = {
                    if (step < SLIDES.lastIndex) {
                        failed = false
                        step++
                    } else {
                        finish(if (failed) destination else FirstStudyDestination.SESSION)
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    IknaTextButton(
                        label = S.t("a11y.001"),
                        enabled = !busy,
                        onClick = {
                            failed = false
                            step--
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (step < SLIDES.lastIndex) {
                    IknaTextButton(
                        label = S.t("onb.010"),
                        enabled = !busy,
                        onClick = {
                            failed = false
                            step = SLIDES.lastIndex
                        },
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
}
