package dev.ikna.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.data.repo.StudyStats
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaReadingCat
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaTextField
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.LocalIknaPalette
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.iknaCardShape
import dev.ikna.ui.theme.iknaNumberStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProfileStatsCardData(
    val profileName: String,
    val headerText: String,
    val subtitle: String,
    val periodLabel: String,
    val generatedDate: String,
    val targets: Int,
    val firstRecorded: Int,
    val answers: Int,
    val repeatAnswers: Int,
    val recalledRepeatAnswers: Int,
    val recoveredCount: Int,
    val labelTargets: String,
    val labelNew: String,
    val labelAnswers: String,
    val labelRecall: String,
    val labelRecallLowSample: String,
    val footerText: String,
    val recoveredText: String,
    val bgArgb: Int,
    val panelArgb: Int,
    val lineArgb: Int,
    val inkArgb: Int,
    val mutedArgb: Int,
    val accentArgb: Int
) {
    val recallPercentText: String
        get() = if (repeatAnswers >= 5) {
            "${(recalledRepeatAnswers * 100 / repeatAnswers)}%"
        } else {
            "—"
        }
}

/** Platform PNG renderer (1200x675) in the active Ikna palette. */
expect fun renderProfileStatsCardPng(data: ProfileStatsCardData): ByteArray

internal val PROFILE_CARD_EMBLEM: Array<String> = arrayOf(
    "###.#A",
    "#.##.#",
    ".##.A#",
    "A..##.",
    "##..#.",
    "..#.#.",
    "..A..."
)

fun buildProfileStatsCardData(
    profileName: String,
    snapshot: StudyStats,
    palette: IknaPalette,
    paletteDisplayName: String
): ProfileStatsCardData {
    val period = snapshot.month
    val zone = runCatching { ZoneId.of(snapshot.zone) }.getOrDefault(ZoneId.systemDefault())
    val dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        .format(Instant.ofEpochMilli(period.until).atZone(zone))
    return ProfileStatsCardData(
        profileName = profileName.trim().ifEmpty { S.t("prof.default_name") },
        headerText = S.t("prof.card_header"),
        subtitle = "$paletteDisplayName · ${S.t("study.002")}",
        periodLabel = S.t("study.002"),
        generatedDate = dateStr,
        targets = period.targets,
        firstRecorded = period.firstRecordedTargets,
        answers = period.answers,
        repeatAnswers = period.repeatAnswers,
        recalledRepeatAnswers = period.recalledRepeatAnswers,
        recoveredCount = period.recovered.size,
        labelTargets = S.t("study.003"),
        labelNew = S.t("study.004"),
        labelAnswers = S.t("prof.metric_answers"),
        labelRecall = S.t("prof.metric_recall"),
        labelRecallLowSample = S.t("stats.recall_sample"),
        footerText = S.t("prof.card_footer").replace("{date}", dateStr),
        recoveredText = S.t("prof.card_recovered").replace("{count}", period.recovered.size.toString()),
        bgArgb = palette.background.toArgb(),
        panelArgb = palette.panel.toArgb(),
        lineArgb = palette.line.toArgb(),
        inkArgb = palette.ink.toArgb(),
        mutedArgb = palette.muted.toArgb(),
        accentArgb = palette.accent.toArgb()
    )
}

/**
 * Calm, local-only profile card placed at the top of Statistics.
 * No accounts, no streaks, no gamified pressure — just an optional nickname,
 * a 30-day factual summary, and a one-tap PNG card export in the active palette.
 */
@Composable
fun IknaLocalProfileCard(
    profileName: String,
    snapshot: StudyStats?,
    paletteDisplayName: String,
    exportingPng: Boolean,
    pngStatus: String?,
    onSaveName: (String) -> Unit,
    onExportPng: (ProfileStatsCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalIknaPalette.current
    val shape = iknaCardShape()
    var editing by remember { mutableStateOf(false) }
    var draft by remember(profileName) { mutableStateOf(profileName) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IknaReadingCat(size = 54.dp, paper = colors.surface)
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = S.t("prof.title"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = profileName.trim().ifEmpty { S.t("prof.default_name") },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground
                )
                Text(
                    text = S.t("prof.local_note"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            IknaTextButton(
                label = S.t(if (editing) "prof.cancel" else "prof.edit_name"),
                color = colors.onSurfaceVariant,
                onClick = {
                    draft = profileName
                    editing = !editing
                }
            )
        }

        if (editing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                IknaTextField(
                    value = draft,
                    onValueChange = { draft = it.take(48) },
                    placeholder = S.t("prof.name_placeholder"),
                    onSearch = {
                        onSaveName(draft)
                        editing = false
                    },
                    modifier = Modifier.weight(1f),
                    maxLength = 48
                )
                IknaWideButton(
                    label = S.t("prof.save_name"),
                    height = 44.dp,
                    filled = true,
                    modifier = Modifier.width(116.dp),
                    onClick = {
                        onSaveName(draft)
                        editing = false
                    }
                )
            }
        }

        if (snapshot != null) {
            val month = snapshot.month
            val recallText = if (month.repeatAnswers >= 5) {
                "${(month.recalledRepeatAnswers * 100 / month.repeatAnswers)}%"
            } else {
                "—"
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                ProfileStatPill(
                    value = month.targets.toString(),
                    label = S.t("prof.pill_targets"),
                    modifier = Modifier.weight(1f)
                )
                ProfileStatPill(
                    value = month.answers.toString(),
                    label = S.t("prof.pill_answers"),
                    modifier = Modifier.weight(1f)
                )
                ProfileStatPill(
                    value = recallText,
                    label = S.t("prof.pill_recall"),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(2.dp))
            IknaWideButton(
                label = S.t("prof.download_png"),
                enabled = !exportingPng,
                height = 46.dp,
                onClick = {
                    onExportPng(
                        buildProfileStatsCardData(
                            profileName = profileName,
                            snapshot = snapshot,
                            palette = palette,
                            paletteDisplayName = paletteDisplayName
                        )
                    )
                }
            )
        }

        if (!pngStatus.isNullOrBlank()) {
            Text(
                text = pngStatus,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileStatPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(colors.background.copy(alpha = 0.55f), iknaCardShape())
            .border(1.dp, colors.outline.copy(alpha = 0.55f), iknaCardShape())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = iknaNumberStyle(MaterialTheme.typography.titleLarge, strong = true),
            color = colors.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}
