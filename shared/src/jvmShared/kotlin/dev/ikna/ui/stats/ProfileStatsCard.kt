package dev.ikna.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ikna.data.repo.ProfileDeckProgress
import dev.ikna.data.repo.StudyStats
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaTextField
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.LocalIknaPalette
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.iknaCardShape
import dev.ikna.ui.theme.iknaNumberStyle
import dev.ikna.ui.theme.iknaTileShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class ProfileStatsCardData(
    val profileName: String,
    val profileBio: String = "",
    val profileAvatar: String = "",
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
    val matureCards: Int = 0,
    val youngCards: Int = 0,
    val learningCards: Int = 0,
    val avgStabilityDays: Double = 0.0,
    val studyMinutes30d: Int = 0,
    val medianSecPerCard30d: Int = 0,
    val activeDays30d: Int = 0,
    val dailyActivity84d: List<Int> = emptyList(),
    val topDecks: List<ProfileDeckProgress> = emptyList(),
    val labelTargets: String,
    val labelNew: String,
    val labelAnswers: String,
    val labelRecall: String,
    val labelRecallLowSample: String,
    val labelMature: String = "",
    val labelStudyTime: String = "",
    val labelActiveDays: String = "",
    val labelHeatmap: String = "",
    val labelMaturity: String = "",
    val labelDecks: String = "",
    val labelYoung: String = "",
    val labelLearning: String = "",
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

    val studyTimeText: String
        get() = formatStudyMinutes(studyMinutes30d)
}

internal fun formatStudyMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0m"
    minutes < 60 -> "${minutes}m"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}h" else "${h}h ${m}m"
    }
}

/** Platform PNG renderer (1200x720) in the active Ikna palette. */
expect fun renderProfileStatsCardPng(data: ProfileStatsCardData): ByteArray

/** Loads a user-picked local avatar image file into a Compose ImageBitmap, or null if absent. */
expect fun loadProfileAvatarBitmap(path: String): ImageBitmap?

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
    paletteDisplayName: String,
    profileBio: String = "",
    profileAvatar: String = ""
): ProfileStatsCardData {
    val period = snapshot.month
    val ins = snapshot.insights
    val zone = runCatching { ZoneId.of(snapshot.zone) }.getOrDefault(ZoneId.systemDefault())
    val dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        .format(Instant.ofEpochMilli(period.until).atZone(zone))
    return ProfileStatsCardData(
        profileName = profileName.trim().ifEmpty { S.t("prof.default_name") },
        profileBio = profileBio.trim(),
        profileAvatar = profileAvatar.trim(),
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
        matureCards = ins.matureCards,
        youngCards = ins.youngCards,
        learningCards = ins.learningCards,
        avgStabilityDays = ins.avgStabilityDays,
        studyMinutes30d = ins.studyMinutes30d,
        medianSecPerCard30d = ins.medianSecPerCard30d,
        activeDays30d = ins.activeDays30d,
        dailyActivity84d = ins.dailyActivity84d,
        topDecks = ins.topDecks,
        labelTargets = S.t("study.003"),
        labelNew = S.t("study.004"),
        labelAnswers = S.t("prof.metric_answers"),
        labelRecall = S.t("prof.metric_recall"),
        labelRecallLowSample = S.t("stats.recall_sample"),
        labelMature = S.t("prof.kpi_mature"),
        labelStudyTime = S.t("prof.kpi_time"),
        labelActiveDays = S.t("prof.kpi_active_days"),
        labelHeatmap = S.t("prof.heatmap_title"),
        labelMaturity = S.t("prof.maturity_title"),
        labelDecks = S.t("prof.decks_title"),
        labelYoung = S.t("prof.stage_young"),
        labelLearning = S.t("prof.stage_learning"),
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
 * Displays the user's custom local avatar photo, or a crisp '+' tile to pick one.
 */
@Composable
fun IknaProfileAvatar(
    avatarPath: String,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    val bitmap = remember(avatarPath) {
        if (avatarPath.isBlank()) null else loadProfileAvatarBitmap(avatarPath)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.background, shape)
            .border(1.dp, if (bitmap != null) colors.outline else colors.primary.copy(alpha = 0.65f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = S.t("prof.pick_avatar"),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val arm = this.size.minDimension * 0.20f
                val stroke = 2.4.dp.toPx()
                drawLine(
                    color = colors.primary,
                    start = Offset(cx - arm, cy),
                    end = Offset(cx + arm, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = colors.primary,
                    start = Offset(cx, cy - arm),
                    end = Offset(cx, cy + arm),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Full local-only Profile & Deep Analytics dashboard.
 * Custom photo avatar (+), nickname, optional study focus, 4 FSRS KPIs,
 * memory maturity bar, 12-week activity heatmap, 7-day forecast, top decks,
 * and 1-tap rich 1200x720 PNG snapshot export.
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
    modifier: Modifier = Modifier,
    profileBio: String = "",
    profileAvatar: String = "",
    onSaveProfile: ((name: String, bio: String) -> Unit)? = null,
    onPickAvatar: (() -> Unit)? = null,
    onRemoveAvatar: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalIknaPalette.current
    val shape = iknaCardShape()
    var editing by remember { mutableStateOf(false) }
    var draftName by remember(profileName) { mutableStateOf(profileName) }
    var draftBio by remember(profileBio) { mutableStateOf(profileBio) }

    val saveProfile = {
        if (onSaveProfile != null) {
            onSaveProfile(draftName.trim(), draftBio.trim())
        } else {
            onSaveName(draftName.trim())
        }
        editing = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        // 1. Profile Header with '+' custom photo avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IknaProfileAvatar(
                avatarPath = profileAvatar,
                size = 64.dp,
                modifier = if (onPickAvatar != null) Modifier.clickable(onClick = onPickAvatar) else Modifier
            )
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = S.t("prof.title"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
                Text(
                    text = profileName.trim().ifEmpty { S.t("prof.default_name") },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profileBio.trim().ifEmpty { S.t("prof.local_note") },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IknaTextButton(
                label = S.t(if (editing) "prof.cancel" else "prof.edit_name"),
                color = colors.onSurfaceVariant,
                onClick = {
                    draftName = profileName
                    draftBio = profileBio
                    editing = !editing
                }
            )
        }

        // 2. Inline Profile Editor (Custom Photo + Nickname + Bio)
        if (editing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background.copy(alpha = 0.55f), iknaTileShape())
                    .border(1.dp, colors.outline.copy(alpha = 0.6f), iknaTileShape())
                    .padding(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                if (onPickAvatar != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IknaTextButton(
                            label = S.t("prof.pick_avatar"),
                            onClick = onPickAvatar
                        )
                        if (profileAvatar.isNotBlank() && onRemoveAvatar != null) {
                            IknaTextButton(
                                label = S.t("prof.remove_avatar"),
                                color = colors.onSurfaceVariant,
                                onClick = onRemoveAvatar
                            )
                        }
                    }
                }
                IknaTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(48) },
                    placeholder = S.t("prof.name_placeholder"),
                    onSearch = saveProfile,
                    modifier = Modifier.fillMaxWidth(),
                    maxLength = 48
                )
                IknaTextField(
                    value = draftBio,
                    onValueChange = { draftBio = it.take(80) },
                    placeholder = S.t("prof.bio_placeholder"),
                    onSearch = saveProfile,
                    modifier = Modifier.fillMaxWidth(),
                    maxLength = 80
                )
                IknaWideButton(
                    label = S.t("prof.save_name"),
                    height = 44.dp,
                    filled = true,
                    onClick = saveProfile
                )
            }
        }

        // 3. Rich Analytics Dashboard
        if (snapshot != null) {
            val month = snapshot.month
            val ins = snapshot.insights
            val recallText = if (month.repeatAnswers >= 5) {
                "${(month.recalledRepeatAnswers * 100 / month.repeatAnswers)}%"
            } else {
                "—"
            }
            val paceSuffix = if (ins.medianSecPerCard30d > 0) " · ${ins.medianSecPerCard30d}s" else ""
            val avgStabText = if (ins.avgStabilityDays >= 1.0) " · ~${ins.avgStabilityDays.roundToInt()}d" else ""

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    ProfileStatPill(
                        value = ins.matureCards.toString(),
                        label = S.t("prof.kpi_mature"),
                        sub = "${month.targets} ${S.t("prof.pill_targets").lowercase()}",
                        accent = true,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatPill(
                        value = recallText,
                        label = S.t("prof.pill_recall"),
                        sub = if (month.repeatAnswers >= 5) "${month.recalledRepeatAnswers}/${month.repeatAnswers}" else "${month.answers} ${S.t("prof.pill_answers").lowercase()}",
                        accent = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    ProfileStatPill(
                        value = formatStudyMinutes(ins.studyMinutes30d),
                        label = S.t("prof.kpi_time"),
                        sub = "${month.answers} ${S.t("prof.pill_answers").lowercase()}$paceSuffix",
                        accent = false,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatPill(
                        value = "${ins.activeDays30d}/30",
                        label = S.t("prof.kpi_active_days"),
                        sub = "${S.t("prof.avg_stability")}$avgStabText",
                        accent = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Memory Maturity Segmented Bar
            val studiedTotal = ins.matureCards + ins.youngCards + ins.learningCards
            if (studiedTotal > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = S.t("prof.maturity_title"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = "$studiedTotal",
                            style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                            color = colors.onSurfaceVariant
                        )
                    }
                    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                        val totalF = studiedTotal.toFloat().coerceAtLeast(1f)
                        val mW = size.width * (ins.matureCards / totalF)
                        val yW = size.width * (ins.youngCards / totalF)
                        val lW = (size.width - mW - yW).coerceAtLeast(0f)
                        val r = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        drawRoundRect(colors.outline.copy(alpha = 0.25f), size = size, cornerRadius = r)
                        if (mW > 0f) drawRect(colors.primary, topLeft = Offset.Zero, size = Size(mW, size.height))
                        if (yW > 0f) drawRect(colors.primary.copy(alpha = 0.45f), topLeft = Offset(mW, 0f), size = Size(yW, size.height))
                        if (lW > 0f) drawRect(colors.onSurfaceVariant.copy(alpha = 0.35f), topLeft = Offset(mW + yW, 0f), size = Size(lW, size.height))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${S.t("prof.kpi_mature")}: ${ins.matureCards}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                        Text(
                            text = "${S.t("prof.stage_young")}: ${ins.youngCards}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = "${S.t("prof.stage_learning")}: ${ins.learningCards}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // 12-Week Calm Activity Heatmap (84 days)
            if (ins.dailyActivity84d.size == 84) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = S.t("prof.heatmap_title"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = "${ins.activeDays84d}/84",
                            style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                            color = colors.onSurfaceVariant
                        )
                    }
                    val maxDay = (ins.dailyActivity84d.maxOrNull() ?: 0).coerceAtLeast(1)
                    Canvas(modifier = Modifier.fillMaxWidth().height(86.dp)) {
                        val cols = 12
                        val rows = 7
                        val gap = 3.dp.toPx()
                        val cellW = (size.width - gap * (cols - 1)) / cols
                        val cellH = (size.height - gap * (rows - 1)) / rows
                        val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        for (i in 0 until 84) {
                            val col = i / 7
                            val row = i % 7
                            val count = ins.dailyActivity84d[i]
                            val level = when {
                                count <= 0 -> 0
                                count <= maxDay / 4 -> 1
                                count <= maxDay / 2 -> 2
                                count <= (maxDay * 3) / 4 -> 3
                                else -> 4
                            }
                            val fill = when (level) {
                                0 -> colors.outline.copy(alpha = 0.22f)
                                1 -> colors.primary.copy(alpha = 0.30f)
                                2 -> colors.primary.copy(alpha = 0.52f)
                                3 -> colors.primary.copy(alpha = 0.76f)
                                else -> colors.primary
                            }
                            drawRoundRect(
                                color = fill,
                                topLeft = Offset(col * (cellW + gap), row * (cellH + gap)),
                                size = Size(cellW, cellH),
                                cornerRadius = corner
                            )
                        }
                    }
                }
            }

            // 7-Day Upcoming Workload Forecast
            if (ins.forecastNext7d.size == 7 && ins.forecastNext7d.any { it > 0 }) {
                val maxDue = (ins.forecastNext7d.maxOrNull() ?: 1).coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = S.t("prof.forecast_title"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = "${ins.forecastNext7d.sum()}",
                            style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                            color = colors.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        ins.forecastNext7d.forEachIndexed { idx, due ->
                            val frac = (due.toFloat() / maxDue.toFloat()).coerceIn(0.08f, 1f)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = if (due > 0) due.toString() else "·",
                                    style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                                    color = if (idx == 0) colors.primary else colors.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((24f * frac).dp)
                                        .background(
                                            if (idx == 0) colors.primary else colors.primary.copy(alpha = 0.42f),
                                            iknaTileShape()
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Top Active Decks Progress
            if (ins.topDecks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = S.t("prof.decks_title"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    ins.topDecks.forEach { deck ->
                        val frac = if (deck.total > 0) (deck.known.toFloat() / deck.total.toFloat()).coerceIn(0f, 1f) else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = deck.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${deck.known}/${deck.total}",
                                    style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .background(colors.outline.copy(alpha = 0.25f))
                            ) {
                                if (frac > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frac)
                                            .height(5.dp)
                                            .background(colors.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                            paletteDisplayName = paletteDisplayName,
                            profileBio = profileBio,
                            profileAvatar = profileAvatar
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
    sub: String = "",
    accent: Boolean = false,
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
            color = if (accent) colors.primary else colors.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
