package dev.ikna.ui.onboarding

import dev.ikna.ui.text.S
import dev.ikna.data.repo.prepareFirstSession
import kotlinx.coroutines.CancellationException

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.ikna.AppContainer
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaScrollableColumn
import dev.ikna.ui.theme.IknaWideButton
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
    val bodyKey: String,
    /** Whether this slide shows the card and its two answers. */
    val demo: Boolean = false
)

private val SLIDES = listOf(
    Slide("onb.001", "onb.002"),
    Slide("onb.003", "onb.004"),
    Slide("onb.005", "onb.006", demo = true),
    Slide("onb.011", "onb.012")
)

/**
 * Four screens, then the first card.
 *
 * The screens keep the author's own order: why ikna exists, how the finite
 * daily plan works, whether the app feels useful and what an error does, then
 * where progress lives and how feedback is welcomed. No configuration is
 * required before the first session.
 *
 * The static answer demo sits beside the third screen because that is where an
 * error and its effect on the next review are explained. The final screen stays
 * visually quiet, with a short starter explanation and an optional Catalogue route.
 */
@Composable
fun OnboardingScreen(container: AppContainer, onDone: (FirstStudyDestination) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(FirstStudyDestination.SESSION) }
    val scope = rememberCoroutineScope()
    val slide = SLIDES[step]

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

        if (slide.demo) {
            Spacer(Modifier.height(24.dp))
            GestureDemo()
        }

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

        // Square marks, like every other mark in the app. These were the only
        // circles left outside Material's own components.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SLIDES.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        // The current mark is wider, not brighter. Four marks of
                        // equal size differing only in alpha is a difference you
                        // have to look for; a long one among short ones is read
                        // without looking.
                        .size(width = if (i == step) 16.dp else 8.dp, height = 8.dp)
                        .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
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
                    step++
                } else {
                    finish(if (failed) destination else FirstStudyDestination.SESSION)
                }
            }
        )

        if (step < SLIDES.lastIndex) {
            IknaTextButton(
                label = S.t("onb.010"),
                onClick = { step = SLIDES.lastIndex },
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

/**
 * The card, drawn the way it will look, with its two answers where they will be.
 *
 * Deliberately not an animation and not a card the user has to swipe to
 * continue: an onboarding screen that demands a gesture before it will let go
 * is a gate, and this one is a label. The words are the exact same two strings
 * the session screen shows (`card.003` and `card.004`), and they carry the same
 * two colours — muted on the left, the accent on the right — so the thing being
 * learned here is the thing that appears there, down to the wording.
 */
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
