package dev.ikna.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.ui.theme.IknaGood
import dev.ikna.ui.theme.IknaMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Slide(val title: String, val body: String)

private val SLIDES = listOf(
    Slide(
        "Фразами, а не словами",
        "Внутри — готовые чанки: короткие живые куски речи. Новые добавляются сами — ничего не надо вставлять руками."
    ),
    Slide(
        "Пропуск — не провал",
        "Если день или неделя пропали, завала на входе не будет. Старое уйдёт в тихий пул и будет возвращаться понемногу, а новые чанки придут только когда будет место."
    ),
    Slide(
        "Минимум — одна карточка",
        "Одна карточка закрывает день целиком. Захочется больше — есть кнопка «ещё немного», и она не сделает завтрашний день тяжелее."
    )
)

/**
 * Three screens, then the first card.
 *
 * It exists to answer the three questions that decide whether the app survives
 * week two — what am I learning, what happens if I disappear, and how little is
 * enough — and it never asks the user to configure anything.
 */
@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val slide = SLIDES[step]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "Ikna",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = slide.body,
            style = MaterialTheme.typography.bodyLarge,
            color = IknaMuted,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SLIDES.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == step) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == step) IknaGood else IknaMuted.copy(alpha = 0.35f))
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (step < SLIDES.lastIndex) {
                    step++
                } else {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // The starter deck installs itself; the first plan is
                            // built before the screen changes, so the user lands
                            // on a card and not on an empty state.
                            runCatching { container.packLoader.installBundledPacks() }
                            runCatching { container.learningRepository.ensureDailyPlan() }
                        }
                        container.settings.setOnboardingDone(true)
                        busy = false
                        onDone()
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    busy -> "Готовлю карточки…"
                    step < SLIDES.lastIndex -> "Дальше"
                    else -> "Начать"
                }
            )
        }

        if (step < SLIDES.lastIndex) {
            TextButton(onClick = { step = SLIDES.lastIndex }) {
                Text("Пропустить", color = IknaMuted)
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}
