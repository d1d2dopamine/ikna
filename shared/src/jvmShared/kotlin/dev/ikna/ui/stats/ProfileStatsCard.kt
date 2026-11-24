package dev.ikna.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
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
        get() = formatStudyMinutesLocalized(studyMinutes30d)
}

internal fun formatStudyMinutesLocalized(minutes: Int): String {
    val uMin = S.t("prof.unit_min")
    val uHour = S.t("prof.unit_hour")
    return when {
        minutes <= 0 -> "0 $uMin"
        minutes < 60 -> "$minutes $uMin"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "$h $uHour" else "$h $uHour $m $uMin"
        }
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
 * Circular user avatar photo, or a quiet recessed '+' circle to choose a photo from the device.
 */
@Composable
fun IknaProfileAvatar(
    avatarPath: String,
    size: Dp = 68.dp,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val bitmap = remember(avatarPath) {
        if (avatarPath.isBlank()) null else loadProfileAvatarBitmap(avatarPath)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.outline.copy(alpha = 0.55f), CircleShape),
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
                val arm = this.size.minDimension * 0.16f
                val stroke = 2.dp.toPx()
                drawLine(
                    color = colors.onSurfaceVariant,
                    start = Offset(cx - arm, cy),
                    end = Offset(cx + arm, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = colors.onSurfaceVariant,
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
 * Open, editorial Local Profile & Learning Overview — zero nested card boxes,
 * true 1:1 square activity heatmap, slender forecast bars, and clean typographic hierarchy.
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
    val hairline = colors.outline.copy(alpha = 0.42f)
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        // 1. Open Profile Header (directly on page canvas — no card wrapper, no redundant eyebrow)
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IknaProfileAvatar(
                    avatarPath = profileAvatar,
                    size = 68.dp,
                    modifier = if (onPickAvatar != null) Modifier.clickable(onClick = onPickAvatar) else Modifier
                )
                Spacer(Modifier.width(Space.md))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = profileName.trim().ifEmpty { S.t("prof.default_name") },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profileBio.trim().ifEmpty { S.t("prof.local_note") },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IknaTextButton(
                    label = S.t(if (editing) "prof.cancel" else "prof.edit_name"),
                    color = colors.primary,
                    horizontalPadding = 0.dp,
                    onClick = {
                        draftName = profileName
                        draftBio = profileBio
                        editing = !editing
                    }
                )
                if (snapshot != null) {
                    IknaTextButton(
                        label = S.t("prof.download_png"),
                        enabled = !exportingPng,
                        color = colors.onSurfaceVariant,
                        horizontalPadding = 0.dp,
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
            }

            if (!pngStatus.isNullOrBlank()) {
                Text(
                    text = pngStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        // 2. Quiet Inline Profile Editor
        if (editing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, iknaTileShape())
                    .padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                if (onPickAvatar != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IknaTextButton(
                            label = S.t("prof.pick_avatar"),
                            horizontalPadding = 0.dp,
                            onClick = onPickAvatar
                        )
                        if (profileAvatar.isNotBlank() && onRemoveAvatar != null) {
                            IknaTextButton(
                                label = S.t("prof.remove_avatar"),
                                color = colors.onSurfaceVariant,
                                horizontalPadding = 0.dp,
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

        if (snapshot != null) {
            val month = snapshot.month
            val ins = snapshot.insights
            val recallText = if (month.repeatAnswers >= 5) {
                "${(month.recalledRepeatAnswers * 100 / month.repeatAnswers)}%"
            } else {
                "—"
            }
            val uDay = S.t("prof.unit_day")
            val stabNote = if (ins.avgStabilityDays >= 1.0) "~${ins.avgStabilityDays.roundToInt()} $uDay" else ""
            val recallNote = if (month.repeatAnswers >= 5) "${month.recalledRepeatAnswers}/${month.repeatAnswers}" else ""

            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))

            // 3. Open Typographic Metric Strip (zero boxes, aligned columns)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 520.dp
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.lg)
                    ) {
                        ProfileMetricColumn(
                            value = ins.matureCards.toString(),
                            label = S.t("prof.kpi_mature"),
                            detail = stabNote,
                            emphasized = ins.matureCards > 0,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricColumn(
                            value = recallText,
                            label = S.t("prof.pill_recall"),
                            detail = recallNote,
                            emphasized = month.repeatAnswers >= 5,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricColumn(
                            value = formatStudyMinutesLocalized(ins.studyMinutes30d),
                            label = S.t("prof.kpi_time"),
                            detail = "${month.answers}",
                            emphasized = false,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileMetricColumn(
                            value = "${ins.activeDays30d}/30",
                            label = S.t("prof.kpi_active_days"),
                            detail = "",
                            emphasized = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            ProfileMetricColumn(
                                value = ins.matureCards.toString(),
                                label = S.t("prof.kpi_mature"),
                                detail = stabNote,
                                emphasized = ins.matureCards > 0,
                                modifier = Modifier.weight(1f)
                            )
                            ProfileMetricColumn(
                                value = recallText,
                                label = S.t("prof.pill_recall"),
                                detail = recallNote,
                                emphasized = month.repeatAnswers >= 5,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            ProfileMetricColumn(
                                value = formatStudyMinutesLocalized(ins.studyMinutes30d),
                                label = S.t("prof.kpi_time"),
                                detail = "${month.answers}",
                                emphasized = false,
                                modifier = Modifier.weight(1f)
                            )
                            ProfileMetricColumn(
                                value = "${ins.activeDays30d}/30",
                                label = S.t("prof.kpi_active_days"),
                                detail = "",
                                emphasized = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. Activity Grid — TRUE 1:1 SQUARE CELLS, never stretched wide bars!
            if (ins.dailyActivity84d.size == 84) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = S.t("prof.heatmap_title"),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onBackground
                        )
                        Text(
                            text = "${ins.activeDays84d} / 84 $uDay",
                            style = iknaNumberStyle(MaterialTheme.typography.bodySmall),
                            color = colors.onSurfaceVariant
                        )
                    }
                    val maxDay = (ins.dailyActivity84d.maxOrNull() ?: 0).coerceAtLeast(1)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val cols = 12
                        val rows = 7
                        val gapDp = 4.dp
                        val maxCell = ((maxWidth - gapDp * (cols - 1)) / cols).coerceIn(10.dp, 18.dp)
                        val gridWidth = maxCell * cols + gapDp * (cols - 1)
                        val gridHeight = maxCell * rows + gapDp * (rows - 1)
                        Canvas(modifier = Modifier.size(width = gridWidth, height = gridHeight)) {
                            val cellPx = maxCell.toPx()
                            val gapPx = gapDp.toPx()
                            val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            for (i in 0 until 84) {
                                val col = i / 7
                                val row = i % 7
                                val count = ins.dailyActivity84d[i]
                                val fill = when {
                                    count <= 0 -> colors.surface
                                    count <= maxDay / 4 -> colors.primary.copy(alpha = 0.28f)
                                    count <= maxDay / 2 -> colors.primary.copy(alpha = 0.52f)
                                    count <= (maxDay * 3) / 4 -> colors.primary.copy(alpha = 0.78f)
                                    else -> colors.primary
                                }
                                drawRoundRect(
                                    color = fill,
                                    topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx)),
                                    size = Size(cellPx, cellPx),
                                    cornerRadius = corner
                                )
                            }
                        }
                    }
                }
            }

            // 5. Memory Maturity & 7-Day Forecast (clean hairline sections, slender vertical bars)
            val studiedTotal = ins.matureCards + ins.youngCards + ins.learningCards
            val hasForecast = ins.forecastNext7d.size == 7 && ins.forecastNext7d.any { it > 0 }
            if (studiedTotal > 0 || hasForecast) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val sideBySide = maxWidth >= 560.dp && studiedTotal > 0 && hasForecast
                    if (sideBySide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.xl),
                            verticalAlignment = Alignment.Top
                        ) {
                            MemoryMaturitySection(
                                mature = ins.matureCards,
                                young = ins.youngCards,
                                learning = ins.learningCards,
                                modifier = Modifier.weight(1f)
                            )
                            ForecastBarsSection(
                                forecast = ins.forecastNext7d,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                            if (studiedTotal > 0) {
                                MemoryMaturitySection(
                                    mature = ins.matureCards,
                                    young = ins.youngCards,
                                    learning = ins.learningCards
                                )
                            }
                            if (hasForecast) {
                                ForecastBarsSection(forecast = ins.forecastNext7d)
                            }
                        }
                    }
                }
            }

            // 6. Active Decks Mastery (clean open rows, thin 3dp progress bars)
            if (ins.topDecks.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = S.t("prof.decks_title"),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onBackground
                    )
                    ins.topDecks.forEach { deck ->
                        val frac = if (deck.total > 0) (deck.known.toFloat() / deck.total.toFloat()).coerceIn(0f, 1f) else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = deck.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    text = "${deck.known} / ${deck.total}",
                                    style = iknaNumberStyle(MaterialTheme.typography.bodySmall),
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(colors.outline.copy(alpha = 0.25f))
                            ) {
                                if (frac > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frac)
                                            .height(3.dp)
                                            .background(colors.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetricColumn(
    value: String,
    label: String,
    detail: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = iknaNumberStyle(MaterialTheme.typography.headlineMedium, strong = true),
            color = if (emphasized) colors.primary else colors.onBackground,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                color = colors.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MemoryMaturitySection(
    mature: Int,
    young: Int,
    learning: Int,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studied = (mature + young + learning).coerceAtLeast(1)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = S.t("prof.maturity_title"),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground
            )
            Text(
                text = studied.toString(),
                style = iknaNumberStyle(MaterialTheme.typography.bodySmall),
                color = colors.onSurfaceVariant
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            val totalF = studied.toFloat()
            val mW = size.width * (mature / totalF)
            val yW = size.width * (young / totalF)
            val lW = (size.width - mW - yW).coerceAtLeast(0f)
            drawRect(colors.outline.copy(alpha = 0.22f), size = size)
            if (mW > 0f) drawRect(colors.primary, topLeft = Offset.Zero, size = Size(mW, size.height))
            if (yW > 0f) drawRect(colors.primary.copy(alpha = 0.48f), topLeft = Offset(mW, 0f), size = Size(yW, size.height))
            if (lW > 0f) drawRect(colors.onSurfaceVariant.copy(alpha = 0.42f), topLeft = Offset(mW + yW, 0f), size = Size(lW, size.height))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            MaturityLegendItem(colors.primary, S.t("prof.kpi_mature"), mature)
            MaturityLegendItem(colors.primary.copy(alpha = 0.48f), S.t("prof.stage_young"), young)
            MaturityLegendItem(colors.onSurfaceVariant.copy(alpha = 0.42f), S.t("prof.stage_learning"), learning)
        }
    }
}

@Composable
private fun MaturityLegendItem(dotColor: Color, label: String, count: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ForecastBarsSection(
    forecast: List<Int>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val maxDue = (forecast.maxOrNull() ?: 1).coerceAtLeast(1)
    val barShape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
    val uDay = S.t("prof.unit_day")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = S.t("prof.forecast_title"),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground
            )
            Text(
                text = forecast.sum().toString(),
                style = iknaNumberStyle(MaterialTheme.typography.bodySmall),
                color = colors.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                forecast.forEachIndexed { idx, due ->
                    val barHeight = if (due > 0) (34f * (due.toFloat() / maxDue.toFloat())).coerceAtLeast(4f).dp else 2.dp
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (due > 0) {
                            Text(
                                text = due.toString(),
                                style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                                color = if (idx == 0) colors.primary else colors.onSurfaceVariant
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(barHeight)
                                .background(
                                    color = when {
                                        due <= 0 -> colors.outline.copy(alpha = 0.25f)
                                        idx == 0 -> colors.primary
                                        else -> colors.primary.copy(alpha = 0.45f)
                                    },
                                    shape = barShape
                                )
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outline.copy(alpha = 0.35f)))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                forecast.indices.forEach { idx ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (idx == 0) S.t("prof.today_short") else "+$idx$uDay",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
    }
}
