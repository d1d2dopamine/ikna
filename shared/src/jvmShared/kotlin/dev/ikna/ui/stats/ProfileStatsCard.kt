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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Brush
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
import dev.ikna.data.repo.ProfileLanguageSummary
import dev.ikna.data.repo.ProfileLearnedPhrase
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

enum class ProfileTab { OVERVIEW, ANALYTICS }

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
    val accentArgb: Int,
    val unitDay: String = "d",
    val labelDeckMatureFmt: String = "{count} mature",
    val labelDeckStudiedFmt: String = "{count} studied",
    val labelDeckReady: String = ""
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

/** Loads a user-picked local image file into a Compose ImageBitmap, or null if absent. */
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
        subtitle = S.t("study.002"),
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
        labelRecall = S.t("prof.pill_recall"),
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
        accentArgb = palette.accent.toArgb(),
        unitDay = S.t("prof.unit_day"),
        labelDeckMatureFmt = S.t("prof.deck_mature_count"),
        labelDeckStudiedFmt = S.t("prof.deck_in_progress"),
        labelDeckReady = S.t("prof.deck_ready")
    )
}

/**
 * Circular user avatar photo, or a quiet recessed '+' circle to choose a photo from the device.
 */
@Composable
fun IknaProfileAvatar(
    avatarPath: String,
    size: Dp = 74.dp,
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
 * Full-fledged local learner Profile with a cover banner hero, overlapping circular avatar,
 * identity meta-line, and clean Overview / Analytics tabs.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    profileBanner: String = "",
    onSaveProfile: ((name: String, bio: String) -> Unit)? = null,
    onPickAvatar: (() -> Unit)? = null,
    onRemoveAvatar: (() -> Unit)? = null,
    onPickBanner: (() -> Unit)? = null,
    onRemoveBanner: (() -> Unit)? = null,
    selectedTab: ProfileTab? = null,
    onSelectTab: ((ProfileTab) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalIknaPalette.current
    val hairline = colors.outline.copy(alpha = 0.42f)
    var editing by remember { mutableStateOf(false) }
    var draftName by remember(profileName) { mutableStateOf(profileName) }
    var draftBio by remember(profileBio) { mutableStateOf(profileBio) }
    var internalTab by remember { mutableStateOf(ProfileTab.OVERVIEW) }
    val activeTab = selectedTab ?: internalTab
    val setTab: (ProfileTab) -> Unit = { tab ->
        if (onSelectTab != null) onSelectTab(tab) else internalTab = tab
    }

    val saveProfile = {
        if (onSaveProfile != null) {
            onSaveProfile(draftName.trim(), draftBio.trim())
        } else {
            onSaveName(draftName.trim())
        }
        editing = false
    }

    val displayName = profileName.trim().ifEmpty { S.t("prof.default_name") }
    val zone = remember(snapshot?.zone) {
        runCatching { ZoneId.of(snapshot?.zone ?: "") }.getOrDefault(ZoneId.systemDefault())
    }
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT) }
    val monthYearFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT) }

    val sinceFmt = S.t("prof.since")
    val totalAnswersFmt = S.t("prof.total_answers_short")
    val identityMeta = remember(snapshot, zone, sinceFmt, totalAnswersFmt) {
        val ins = snapshot?.insights
        if (ins == null) {
            ""
        } else {
            val parts = mutableListOf<String>()
            ins.memberSinceMs?.let { ts ->
                val label = monthYearFmt.format(Instant.ofEpochMilli(ts).atZone(zone))
                parts += sinceFmt.replace("{date}", label)
            }
            if (ins.totalAnswersAllTime > 0) {
                parts += totalAnswersFmt.replace("{count}", ins.totalAnswersAllTime.toString())
            }
            parts.joinToString(" · ")
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        // 1. Hero Profile Header: cover banner + overlapping circular avatar + balanced actions
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ProfileCoverBanner(
                    bannerPath = profileBanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .then(
                            if (editing && onPickBanner != null) Modifier.clickable(onClick = onPickBanner) else Modifier
                        )
                )

                // Overlapping avatar + name/bio/actions row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 66.dp, start = 16.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .background(colors.background, CircleShape)
                            .padding(3.dp)
                    ) {
                        IknaProfileAvatar(
                            avatarPath = profileAvatar,
                            size = 76.dp,
                            modifier = if (onPickAvatar != null) {
                                Modifier.clickable(onClick = onPickAvatar)
                            } else {
                                Modifier
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 42.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stacked = maxWidth < 340.dp
                            if (stacked) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    ProfileHeaderActions(
                                        editing = editing,
                                        snapshot = snapshot,
                                        exportingPng = exportingPng,
                                        onToggleEdit = {
                                            editing = !editing
                                            if (editing) {
                                                draftName = profileName
                                                draftBio = profileBio
                                            }
                                        },
                                        onExportPng = { snap ->
                                            onExportPng(
                                                buildProfileStatsCardData(
                                                    profileName = profileName,
                                                    snapshot = snap,
                                                    palette = palette,
                                                    paletteDisplayName = paletteDisplayName,
                                                    profileBio = profileBio,
                                                    profileAvatar = profileAvatar
                                                )
                                            )
                                        }
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(Space.sm))
                                    ProfileHeaderActions(
                                        editing = editing,
                                        snapshot = snapshot,
                                        exportingPng = exportingPng,
                                        onToggleEdit = {
                                            editing = !editing
                                            if (editing) {
                                                draftName = profileName
                                                draftBio = profileBio
                                            }
                                        },
                                        onExportPng = { snap ->
                                            onExportPng(
                                                buildProfileStatsCardData(
                                                    profileName = profileName,
                                                    snapshot = snap,
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
                        }

                        Text(
                            text = profileBio.trim().ifEmpty { S.t("prof.local_note") },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (identityMeta.isNotBlank()) {
                            Text(
                                text = identityMeta,
                                style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                                color = colors.onSurfaceVariant.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (!pngStatus.isNullOrBlank()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = pngStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary
                )
            }
        }

        // 2. Inline Profile Editor (Avatar + Cover Banner + Name + Bio)
        if (editing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.90f), iknaTileShape())
                    .border(1.dp, hairline, iknaTileShape())
                    .padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onPickAvatar != null) {
                        IknaTextButton(
                            label = S.t("prof.pick_avatar"),
                            color = colors.primary,
                            horizontalPadding = 0.dp,
                            onClick = onPickAvatar
                        )
                    }
                    if (profileAvatar.isNotBlank() && onRemoveAvatar != null) {
                        IknaTextButton(
                            label = S.t("prof.remove_avatar"),
                            color = colors.onSurfaceVariant,
                            horizontalPadding = 0.dp,
                            onClick = onRemoveAvatar
                        )
                    }
                    if (onPickBanner != null) {
                        IknaTextButton(
                            label = S.t("prof.pick_banner"),
                            color = colors.primary,
                            horizontalPadding = 0.dp,
                            onClick = onPickBanner
                        )
                    }
                    if (profileBanner.isNotBlank() && onRemoveBanner != null) {
                        IknaTextButton(
                            label = S.t("prof.remove_banner"),
                            color = colors.onSurfaceVariant,
                            horizontalPadding = 0.dp,
                            onClick = onRemoveBanner
                        )
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
            val uDay = S.t("prof.unit_day")

            // 3. Clean Editorial 2-Tab Switcher: Profile (Overview) | Analytics
            ProfileTabBar(
                selected = activeTab,
                onSelect = setTab,
                hairline = hairline
            )

            if (activeTab == ProfileTab.OVERVIEW) {
                // OVERVIEW TAB: Languages + Recently Practiced Phrases + Decks + Study Rhythm + Milestones
                if (ins.languages.isNotEmpty()) {
                    LanguagesSection(languages = ins.languages)
                }

                if (ins.recentLearned.isNotEmpty()) {
                    if (ins.languages.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                    }
                    RecentPhrasesSection(phrases = ins.recentLearned, hairline = hairline)
                }

                if (ins.topDecks.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                    ProfileDecksSection(decks = ins.topDecks, hairline = hairline)
                }

                val hasRhythm = ins.rhythmBuckets.size == 4 && ins.rhythmBuckets.any { it > 0 }
                val milestones = buildList<Pair<String, String>> {
                    ins.memberSinceMs?.let { add(S.t("prof.milestone_first") to dateFmt.format(Instant.ofEpochMilli(it).atZone(zone))) }
                    ins.firstMatureAtMs?.let { add(S.t("prof.milestone_mature") to dateFmt.format(Instant.ofEpochMilli(it).atZone(zone))) }
                    ins.hundredthAnswerAtMs?.let { add(S.t("prof.milestone_hundred") to dateFmt.format(Instant.ofEpochMilli(it).atZone(zone))) }
                }

                if (hasRhythm || milestones.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val sideBySide = maxWidth >= 560.dp && hasRhythm && milestones.isNotEmpty()
                        if (sideBySide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.xl),
                                verticalAlignment = Alignment.Top
                            ) {
                                StudyRhythmSection(
                                    buckets = ins.rhythmBuckets,
                                    modifier = Modifier.weight(1f)
                                )
                                MilestonesSection(
                                    milestones = milestones,
                                    hairline = hairline,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                if (hasRhythm) StudyRhythmSection(buckets = ins.rhythmBuckets)
                                if (milestones.isNotEmpty()) MilestonesSection(milestones = milestones, hairline = hairline)
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline.copy(alpha = 0.5f)))
                Text(
                    text = S.t("prof.device_local_footer"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.72f)
                )
            } else {
                // ANALYTICS TAB: KPI Strip + Side-by-side 12-Week Heatmap, Maturity & 7-Day Forecast
                val recallText = if (month.repeatAnswers >= 5) {
                    "${(month.recalledRepeatAnswers * 100 / month.repeatAnswers)}%"
                } else {
                    "—"
                }
                val stabNote = if (ins.avgStabilityDays >= 1.0) "~${ins.avgStabilityDays.roundToInt()} $uDay" else ""
                val recallNote = if (month.repeatAnswers >= 5) "${month.recalledRepeatAnswers}/${month.repeatAnswers}" else ""

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

                val hasHeatmap = ins.dailyActivity84d.size == 84
                val studiedTotal = ins.matureCards + ins.youngCards + ins.learningCards
                val hasForecast = ins.forecastNext7d.size == 7 && ins.forecastNext7d.any { it > 0 }
                val hasRightCol = studiedTotal > 0 || hasForecast
                if (hasHeatmap || hasRightCol) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val sideBySide = maxWidth >= 560.dp && hasHeatmap && hasRightCol
                        if (sideBySide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.xl),
                                verticalAlignment = Alignment.Top
                            ) {
                                ActivityHeatmapSection(
                                    dailyActivity84d = ins.dailyActivity84d,
                                    activeDays84d = ins.activeDays84d,
                                    uDay = uDay,
                                    modifier = Modifier.weight(1.05f)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                                ) {
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
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                if (hasHeatmap) {
                                    ActivityHeatmapSection(
                                        dailyActivity84d = ins.dailyActivity84d,
                                        activeDays84d = ins.activeDays84d,
                                        uDay = uDay
                                    )
                                }
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
            }
        }
    }
}

@Composable
private fun ProfileCoverBanner(
    bannerPath: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    val bitmap = remember(bannerPath) {
        if (bannerPath.isBlank()) null else loadProfileAvatarBitmap(bannerPath)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline.copy(alpha = 0.35f), shape)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.background.copy(alpha = 0.42f)
                            )
                        )
                    )
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.surface,
                            colors.primary.copy(alpha = 0.14f),
                            colors.surface
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
                val lineColor = colors.primary.copy(alpha = 0.16f)
                val mutedLine = colors.outline.copy(alpha = 0.18f)
                val stroke = 1.dp.toPx()
                drawLine(
                    color = mutedLine,
                    start = Offset(size.width * 0.56f, 0f),
                    end = Offset(size.width * 0.44f, size.height),
                    strokeWidth = stroke
                )
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.72f, 0f),
                    end = Offset(size.width * 0.60f, size.height),
                    strokeWidth = stroke
                )
                drawLine(
                    color = mutedLine,
                    start = Offset(size.width * 0.86f, 0f),
                    end = Offset(size.width * 0.74f, size.height),
                    strokeWidth = stroke
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderActions(
    editing: Boolean,
    snapshot: StudyStats?,
    exportingPng: Boolean,
    onToggleEdit: () -> Unit,
    onExportPng: (StudyStats) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IknaTextButton(
            label = S.t(if (editing) "prof.cancel" else "prof.edit_name"),
            color = colors.onBackground,
            horizontalPadding = 0.dp,
            onClick = onToggleEdit
        )
        if (snapshot != null) {
            IknaTextButton(
                label = if (exportingPng) S.t("bk.005") else S.t("prof.download_png"),
                enabled = !exportingPng,
                color = colors.onSurfaceVariant,
                horizontalPadding = 0.dp,
                onClick = { onExportPng(snapshot) }
            )
        }
    }
}

@Composable
private fun ProfileTabBar(
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
    hairline: Color
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            listOf(
                ProfileTab.OVERVIEW to S.t("prof.tab_overview"),
                ProfileTab.ANALYTICS to S.t("prof.tab_analytics")
            ).forEach { (tab, title) ->
                val isSelected = selected == tab
                Column(
                    modifier = Modifier
                        .clickable { onSelect(tab) }
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.onBackground else colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(2.dp)
                            .background(if (isSelected) colors.primary else Color.Transparent)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagesSection(
    languages: List<ProfileLanguageSummary>
) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = S.t("prof.languages_title"),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            languages.forEach { item ->
                val status = when {
                    item.mature > 0 -> S.t("prof.deck_mature_count").replace("{count}", item.mature.toString())
                    item.studied > 0 -> S.t("prof.deck_in_progress").replace("{count}", item.studied.toString())
                    else -> S.t("prof.deck_ready")
                }
                Row(
                    modifier = Modifier
                        .background(colors.surface.copy(alpha = 0.85f), shape)
                        .border(1.dp, colors.outline.copy(alpha = 0.35f), shape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.lang.uppercase(Locale.ROOT),
                        style = iknaNumberStyle(MaterialTheme.typography.labelMedium, strong = true),
                        color = if (item.mature > 0) colors.primary else colors.onBackground
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentPhrasesSection(
    phrases: List<ProfileLearnedPhrase>,
    hairline: Color
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = S.t("prof.recent_phrases_title"),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground
        )
        phrases.forEachIndexed { idx, phrase ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (phrase.isMature) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.45f),
                            shape = CircleShape
                        )
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = phrase.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (phrase.translation.isNotBlank()) {
                        Text(
                            text = phrase.translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (phrase.lang.isNotBlank()) {
                    Text(
                        text = phrase.lang.uppercase(Locale.ROOT),
                        style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                        color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (idx < phrases.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
private fun ProfileDecksSection(
    decks: List<ProfileDeckProgress>,
    hairline: Color
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = S.t("prof.decks_title"),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground
        )
        decks.forEachIndexed { idx, deck ->
            val statusText = formatDeckProgressQuiet(
                deck = deck,
                matureFmt = S.t("prof.deck_mature_count"),
                studiedFmt = S.t("prof.deck_in_progress"),
                readyLabel = S.t("prof.deck_ready")
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
                if (statusText.isNotBlank()) {
                    Spacer(Modifier.width(Space.md))
                    Text(
                        text = statusText,
                        style = iknaNumberStyle(MaterialTheme.typography.bodySmall),
                        color = if (deck.known > 0) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (idx < decks.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
private fun StudyRhythmSection(
    buckets: List<Int>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val maxVal = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
    val total = buckets.sum().coerceAtLeast(1)
    val labels = listOf(
        S.t("prof.rhythm_morning"),
        S.t("prof.rhythm_afternoon"),
        S.t("prof.rhythm_evening"),
        S.t("prof.rhythm_night")
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = S.t("prof.rhythm_title"),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground
        )
        buckets.forEachIndexed { idx, count ->
            val frac = (count.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
            val pct = (count * 100) / total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = labels[idx],
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(68.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(colors.outline.copy(alpha = 0.22f), RoundedCornerShape(3.dp))
                ) {
                    if (frac > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(6.dp)
                                .background(
                                    color = if (count == maxVal) colors.primary else colors.primary.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                Text(
                    text = if (count > 0) "$pct%" else "—",
                    style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                    color = if (count == maxVal) colors.onBackground else colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
    }
}

@Composable
private fun MilestonesSection(
    milestones: List<Pair<String, String>>,
    hairline: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = S.t("prof.milestones_title"),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground
        )
        milestones.forEachIndexed { idx, (label, date) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = date,
                    style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (idx < milestones.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline.copy(alpha = 0.25f)))
            }
        }
    }
}

internal fun formatDeckProgressQuiet(
    deck: ProfileDeckProgress,
    matureFmt: String,
    studiedFmt: String,
    readyLabel: String
): String = when {
    deck.known > 0 -> matureFmt.replace("{count}", deck.known.toString())
    deck.studied > 0 -> studiedFmt.replace("{count}", deck.studied.toString())
    else -> readyLabel
}

@Composable
private fun ActivityHeatmapSection(
    dailyActivity84d: List<Int>,
    activeDays84d: Int,
    uDay: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val maxDay = (dailyActivity84d.maxOrNull() ?: 0).coerceAtLeast(1)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cols = 12
        val rows = 7
        val gapDp = 4.dp
        val cellDp = ((maxWidth - gapDp * (cols - 1)) / cols).coerceIn(10.dp, 24.dp)
        val gridWidth = cellDp * cols + gapDp * (cols - 1)
        val gridHeight = cellDp * rows + gapDp * (rows - 1)
        Column(
            modifier = Modifier.width(gridWidth),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.t("prof.heatmap_title"),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = "$activeDays84d / 84 $uDay",
                    style = iknaNumberStyle(MaterialTheme.typography.bodySmall),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Canvas(modifier = Modifier.size(width = gridWidth, height = gridHeight)) {
                val cellPx = cellDp.toPx()
                val gapPx = gapDp.toPx()
                val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                for (i in 0 until 84) {
                    val col = i / 7
                    val row = i % 7
                    val count = dailyActivity84d[i]
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

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
            color = colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
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
                        verticalArrangement = Alignment.Bottom
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
