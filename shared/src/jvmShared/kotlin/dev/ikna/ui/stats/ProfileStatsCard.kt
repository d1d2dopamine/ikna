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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    onSelectBannerPreset: ((String) -> Unit)? = null,
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
    val inMemoryFmt = S.t("prof.in_memory_short")
    val activeDaysFmt = S.t("prof.active_days_short")
    val identityMeta = remember(snapshot, zone, sinceFmt, totalAnswersFmt, inMemoryFmt, activeDaysFmt) {
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
            val inMemory = ins.matureCards + ins.youngCards + ins.learningCards
            if (inMemory > 0) {
                parts += inMemoryFmt.replace("{count}", inMemory.toString())
            }
            if (ins.activeDays84d > 0) {
                parts += activeDaysFmt.replace("{count}", ins.activeDays84d.toString())
            }
            parts.joinToString(" · ")
        }
    }

    val badges = remember(snapshot, zone, S.lang, S.pseudo) {
        buildFactualProfileBadges(snapshot?.insights, zone, dateFmt)
    }
    val earnedBadges = remember(badges) { badges.filter { it.earned } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        // 1. Hero Profile Header (X / Discord-inspired silhouette, quiet Ikna craft)
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ProfileCoverBanner(
                    bannerPath = profileBanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .then(
                            if (editing && onPickBanner != null) Modifier.clickable(onClick = onPickBanner) else Modifier
                        )
                )

                // Overlapping avatar + top-right badge tray & outlined pill actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 92.dp, start = 16.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(colors.background, CircleShape)
                            .padding(4.dp)
                    ) {
                        IknaProfileAvatar(
                            avatarPath = profileAvatar,
                            size = 80.dp,
                            modifier = if (onPickAvatar != null) {
                                Modifier.clickable(onClick = onPickAvatar)
                            } else {
                                Modifier
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (earnedBadges.isNotEmpty()) {
                            ProfileHeaderBadgeTray(earnedBadges = earnedBadges)
                        }
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

            // Identity block directly beneath avatar row (X-style clarity, zero clutter)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
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

                if (identityMeta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = identityMeta,
                        style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                        color = colors.onSurfaceVariant.copy(alpha = 0.80f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
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

        // 2. Inline Profile Editor (Avatar + Cover Banner Presets / Upload + Name + Bio)
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
                if (onSelectBannerPreset != null) {
                    Text(
                        text = S.t("prof.banner_presets"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    val activePreset = resolveBannerPresetId(profileBanner)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PROFILE_BANNER_PRESETS.forEach { preset ->
                            val isSelected = activePreset == preset.id
                            val swatchShape = iknaTileShape()
                            Box(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(36.dp)
                                    .clip(swatchShape)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colors.primary else colors.outline.copy(alpha = 0.38f),
                                        shape = swatchShape
                                    )
                                    .clickable { onSelectBannerPreset(preset.id) }
                            ) {
                                ProfileBannerPresetCanvas(
                                    presetId = preset.id,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
            val uDay = S.t("prof.unit_day")

            // 3. Clean Editorial 2-Tab Switcher: Profile (Overview) | Analytics
            ProfileTabBar(
                selected = activeTab,
                onSelect = setTab,
                hairline = hairline
            )

            if (activeTab == ProfileTab.OVERVIEW) {
                // OVERVIEW TAB: Languages + Factual Badges + Study Rhythm + Milestones in clean panels
                if (ins.languages.isNotEmpty()) {
                    ProfileSurfacePanel(hairline = hairline) {
                        LanguagesSection(languages = ins.languages)
                    }
                }

                if (badges.isNotEmpty()) {
                    ProfileSurfacePanel(hairline = hairline) {
                        ProfileBadgesSection(badges = badges)
                    }
                }

                val hasRhythm = ins.rhythmBuckets.size == 4 && ins.rhythmBuckets.any { it > 0 }
                val milestones = buildList<Pair<String, String>> {
                    ins.memberSinceMs?.let { add(S.t("prof.milestone_first") to dateFmt.format(Instant.ofEpochMilli(it).atZone(zone))) }
                    ins.firstMatureAtMs?.let { add(S.t("prof.milestone_mature") to dateFmt.format(Instant.ofEpochMilli(it).atZone(zone))) }
                    ins.hundredthAnswerAtMs?.let { add(S.t("prof.milestone_hundred") to dateFmt.format(Instant.ofEpochMilli(it).atZone(zone))) }
                    ins.fiveHundredthAnswerAtMs?.let { add(S.t("prof.milestone_five_hundred") to dateFmt.format(Instant.ofEpochMilli(it).atZone(zone))) }
                }

                if (hasRhythm || milestones.isNotEmpty()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val sideBySide = maxWidth >= 560.dp && hasRhythm && milestones.isNotEmpty()
                        if (sideBySide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.md),
                                verticalAlignment = Alignment.Top
                            ) {
                                ProfileSurfacePanel(hairline = hairline, modifier = Modifier.weight(1f)) {
                                    StudyRhythmSection(buckets = ins.rhythmBuckets)
                                }
                                ProfileSurfacePanel(hairline = hairline, modifier = Modifier.weight(1f)) {
                                    MilestonesSection(milestones = milestones, hairline = hairline)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                                if (hasRhythm) {
                                    ProfileSurfacePanel(hairline = hairline) {
                                        StudyRhythmSection(buckets = ins.rhythmBuckets)
                                    }
                                }
                                if (milestones.isNotEmpty()) {
                                    ProfileSurfacePanel(hairline = hairline) {
                                        MilestonesSection(milestones = milestones, hairline = hairline)
                                    }
                                }
                            }
                        }
                    }
                }
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

internal data class ProfileBannerPreset(val id: String)

internal val PROFILE_BANNER_PRESETS: List<ProfileBannerPreset> = listOf(
    ProfileBannerPreset("obsidian"),
    ProfileBannerPreset("horizon"),
    ProfileBannerPreset("aurora"),
    ProfileBannerPreset("topo"),
    ProfileBannerPreset("grid"),
    ProfileBannerPreset("prism")
)

internal fun resolveBannerPresetId(bannerPath: String): String? = when {
    bannerPath.isBlank() -> "obsidian"
    bannerPath.startsWith("preset:") -> bannerPath.removePrefix("preset:").ifBlank { "obsidian" }
    else -> null
}

@Composable
private fun ProfileSurfacePanel(
    hairline: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.84f), shape)
            .border(1.dp, hairline, shape)
            .padding(Space.md)
    ) {
        content()
    }
}

@Composable
private fun ProfileCoverBanner(
    bannerPath: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    val presetId = remember(bannerPath) { resolveBannerPresetId(bannerPath) }
    val bitmap = remember(bannerPath, presetId) {
        if (presetId != null || bannerPath.isBlank()) null else loadProfileAvatarBitmap(bannerPath)
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
            ProfileBannerPresetCanvas(
                presetId = presetId ?: "obsidian",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val BANNER_PIXEL_EMBLEM: Array<String> = arrayOf(
    "###.#A",
    "#.##.#",
    ".##.A#",
    "A..##.",
    "##..#.",
    "..#.#.",
    "..A..."
)

@Composable
private fun ProfileBannerPresetCanvas(
    presetId: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalIknaPalette.current
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val bg = palette.background
        val ink = palette.ink
        val accent = palette.accent
        fun tint(fg: Color, alpha: Float): Color = fg.copy(alpha = alpha).compositeOver(bg)
        val surface = tint(ink, 0.06f)
        val deepSurface = tint(ink, 0.03f)
        val hairline = (h * 0.008f).coerceIn(0.75.dp.toPx(), 1.25.dp.toPx())

        when (presetId) {
            "horizon" -> {
                // Editorial linen strata: quiet stepped horizontal bands + single golden-ratio horizon rule.
                val bands = floatArrayOf(0f, 0.24f, 0.46f, 0.64f, 0.82f, 1f)
                val tones = arrayOf(
                    tint(ink, 0.03f),
                    tint(ink, 0.055f),
                    tint(ink, 0.085f),
                    tint(accent, 0.075f),
                    tint(ink, 0.035f)
                )
                for (i in tones.indices) {
                    val y0 = h * bands[i]
                    val y1 = h * bands[i + 1]
                    drawRect(tones[i], Offset(0f, y0), Size(w, y1 - y0 + 0.5f))
                }
                for (i in 1 until bands.size - 1) {
                    val y = h * bands[i]
                    val isHorizon = i == 3
                    drawLine(
                        color = if (isHorizon) accent.copy(alpha = 0.58f) else ink.copy(alpha = 0.09f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = if (isHorizon) hairline * 1.3f else hairline
                    )
                }
                // Crisp registration tick at right edge of the horizon line.
                val tickH = h * 0.14f
                val hy = h * bands[3]
                drawLine(
                    color = accent.copy(alpha = 0.72f),
                    start = Offset(w * 0.88f, hy - tickH * 0.5f),
                    end = Offset(w * 0.88f, hy + tickH * 0.5f),
                    strokeWidth = hairline * 1.2f
                )
            }
            "aurora" -> {
                // Swiss halftone pixel-wave: crisp square matrix modulated by a calm diagonal density field.
                drawRect(deepSurface)
                val cols = 28
                val rows = 9
                val stepX = w / cols
                val stepY = h / rows
                val maxCell = minOf(stepX, stepY) * 0.62f
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val nx = (c + 0.5f) / cols
                        val ny = (r + 0.5f) / rows
                        // Keep bottom-left calm behind the overlapping profile avatar.
                        val avatarClear = ((nx - 0.14f) * (nx - 0.14f) * 2.2f + (ny - 0.88f) * (ny - 0.88f) * 2.6f)
                        val wave = 0.5f + 0.5f * sin((nx * 2.4f - ny * 1.3f) * PI.toFloat())
                        val ridge = 0.5f + 0.5f * cos((nx * 1.7f + ny * 2.1f) * PI.toFloat())
                        val density = (wave * 0.65f + ridge * 0.35f) * (nx * 0.75f + 0.25f)
                        if (avatarClear < 0.09f || density < 0.24f) continue
                        val cell = maxCell * ((density - 0.20f) / 0.80f).coerceIn(0.22f, 1f)
                        val cx = (c + 0.5f) * stepX - cell / 2f
                        val cy = (r + 0.5f) * stepY - cell / 2f
                        val isAccentBand = density > 0.78f && ((c + r) % 2 == 0)
                        val color = if (isAccentBand) {
                            accent.copy(alpha = (0.34f + (density - 0.78f) * 1.4f).coerceAtMost(0.68f))
                        } else {
                            ink.copy(alpha = (0.05f + density * 0.14f).coerceAtMost(0.20f))
                        }
                        drawRect(color, Offset(cx, cy), Size(cell, cell))
                    }
                }
            }
            "topo" -> {
                // Swiss cartographic ridgelines: crisp parallel harmonic contour engravings across the banner.
                drawRect(surface)
                val lineCount = 7
                val samples = 64
                for (i in 0 until lineCount) {
                    val t = i.toFloat() / (lineCount - 1)
                    val baseY = h * (0.20f + t * 0.66f)
                    val amp1 = h * (0.065f + (1f - t) * 0.045f)
                    val amp2 = h * 0.032f
                    val phase = i * 0.42f
                    val path = Path()
                    for (s in 0..samples) {
                        val u = s.toFloat() / samples
                        val x = u * w
                        val envelope = 0.35f + 0.65f * sin(u * PI.toFloat())
                        val dy = (sin(u * 2.6f * PI.toFloat() + phase) * amp1 +
                            cos(u * 5.2f * PI.toFloat() - phase * 0.7f) * amp2) * envelope
                        val y = baseY + dy
                        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val isFocal = i == 3
                    drawPath(
                        path = path,
                        color = if (isFocal) accent.copy(alpha = 0.56f) else ink.copy(alpha = 0.08f + (1f - kotlin.math.abs(t - 0.5f) * 2f) * 0.10f),
                        style = Stroke(
                            width = if (isFocal) hairline * 1.45f else hairline,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
            "grid" -> {
                // Precision drafting index card: micro crosshair intersections + architectural framing + focal cluster.
                drawRect(deepSurface)
                val cols = 12
                val rows = 4
                val stepX = w / cols
                val stepY = h / rows
                val crossArm = minOf(stepX, stepY) * 0.11f
                // Fine horizontal baseline rules (index-card rhythm)
                for (r in 1 until rows) {
                    val y = r * stepY
                    drawLine(ink.copy(alpha = 0.07f), Offset(0f, y), Offset(w, y), strokeWidth = hairline * 0.8f)
                }
                // Precision '+' registration crosshairs at intersections
                for (r in 1 until rows) {
                    for (c in 1 until cols) {
                        val ix = c * stepX
                        val iy = r * stepY
                        val emphasis = c >= cols - 4 && r <= 2
                        val markColor = if (emphasis) accent.copy(alpha = 0.42f) else ink.copy(alpha = 0.18f)
                        drawLine(markColor, Offset(ix - crossArm, iy), Offset(ix + crossArm, iy), hairline)
                        drawLine(markColor, Offset(ix, iy - crossArm), Offset(ix, iy + crossArm), hairline)
                    }
                }
                // Highlighted coordinate cell on upper-right
                val cellX = (cols - 3) * stepX
                val cellY = 1f * stepY
                drawRect(
                    color = accent.copy(alpha = 0.10f),
                    topLeft = Offset(cellX, cellY),
                    size = Size(stepX, stepY)
                )
                drawRect(
                    color = accent.copy(alpha = 0.45f),
                    topLeft = Offset(cellX, cellY),
                    size = Size(stepX, stepY),
                    style = Stroke(width = hairline)
                )
            }
            "prism" -> {
                // Bauhaus architectural louvers: golden-proportion vertical pillars + narrow accent light slit.
                drawRect(bg)
                val cuts = floatArrayOf(0f, 0.14f, 0.25f, 0.41f, 0.53f, 0.68f, 0.705f, 0.83f, 1f)
                val alphas = floatArrayOf(0.02f, 0.05f, 0.03f, 0.07f, 0.04f, -1f, 0.08f, 0.035f)
                for (i in alphas.indices) {
                    val x0 = w * cuts[i]
                    val x1 = w * cuts[i + 1]
                    val fillColor = if (alphas[i] < 0f) {
                        tint(accent, 0.22f)
                    } else {
                        tint(ink, alphas[i])
                    }
                    drawRect(fillColor, Offset(x0, 0f), Size(x1 - x0 + 0.5f, h))
                    if (i > 0) {
                        val isSlitEdge = alphas[i] < 0f || (i > 0 && alphas[i - 1] < 0f)
                        drawLine(
                            color = if (isSlitEdge) accent.copy(alpha = 0.55f) else ink.copy(alpha = 0.08f),
                            start = Offset(x0, 0f),
                            end = Offset(x0, h),
                            strokeWidth = hairline
                        )
                    }
                }
                // Subtle architectural top/bottom ticks aligning the focal column
                val slitCenter = w * ((cuts[5] + cuts[6]) * 0.5f)
                drawLine(accent.copy(alpha = 0.70f), Offset(slitCenter, 0f), Offset(slitCenter, h * 0.16f), hairline * 1.4f)
            }
            else -> {
                // "obsidian" default: calm Ikna bookplate featuring the signature pixel emblem on the right third.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(surface, deepSurface, bg)
                    )
                )
                // Quiet architectural rule anchoring the bottom third
                val ruleY = h * 0.74f
                drawLine(
                    color = ink.copy(alpha = 0.10f),
                    start = Offset(w * 0.06f, ruleY),
                    end = Offset(w * 0.94f, ruleY),
                    strokeWidth = hairline
                )
                // Crisp Ikna 6x7 pixel emblem rendered in architectural scale on the right side
                val rows = BANNER_PIXEL_EMBLEM.size
                val cols = BANNER_PIXEL_EMBLEM[0].length
                val blockH = h * 0.64f
                val step = blockH / rows
                val cell = step * 0.74f
                val originX = w * 0.92f - cols * step
                val originY = (h - rows * step) * 0.44f
                for (r in 0 until rows) {
                    val rowStr = BANNER_PIXEL_EMBLEM[r]
                    for (c in 0 until cols) {
                        val ch = rowStr[c]
                        if (ch == '.') continue
                        val x = originX + c * step + (step - cell) * 0.5f
                        val y = originY + r * step + (step - cell) * 0.5f
                        val color = if (ch == 'A') accent.copy(alpha = 0.62f) else ink.copy(alpha = 0.15f)
                        drawRect(color, Offset(x, y), Size(cell, cell))
                    }
                }
                // Subtle secondary echo grid on upper-center for depth without clutter
                val echoStep = step * 0.55f
                val echoCell = echoStep * 0.68f
                val echoX = originX - cols * echoStep - w * 0.06f
                val echoY = originY + h * 0.08f
                for (r in 0 until rows) {
                    val rowStr = BANNER_PIXEL_EMBLEM[r]
                    for (c in 0 until cols) {
                        val ch = rowStr[c]
                        if (ch == '.') continue
                        val x = echoX + c * echoStep
                        val y = echoY + r * echoStep
                        val color = if (ch == 'A') accent.copy(alpha = 0.22f) else ink.copy(alpha = 0.06f)
                        drawRect(color, Offset(x, y), Size(echoCell, echoCell))
                    }
                }
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
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(pillShape)
                .background(if (editing) colors.primary.copy(alpha = 0.14f) else colors.surface.copy(alpha = 0.85f), pillShape)
                .border(1.dp, if (editing) colors.primary.copy(alpha = 0.65f) else colors.outline.copy(alpha = 0.55f), pillShape)
                .clickable(onClick = onToggleEdit)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = S.t(if (editing) "prof.cancel" else "prof.edit_name"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (editing) colors.primary else colors.onBackground,
                maxLines = 1,
                softWrap = false
            )
        }
        if (snapshot != null) {
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(colors.surface.copy(alpha = 0.72f), pillShape)
                    .border(1.dp, colors.outline.copy(alpha = 0.40f), pillShape)
                    .then(if (!exportingPng) Modifier.clickable { onExportPng(snapshot) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (exportingPng) S.t("bk.005") else S.t("prof.download_png"),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

internal enum class ProfileBadgeKind {
    FIRST_SESSION,
    HUNDRED_ANSWERS,
    FIRST_MATURE,
    MULTILINGUAL,
    FIVE_HUNDRED_ANSWERS
}

internal data class FactualProfileBadge(
    val kind: ProfileBadgeKind,
    val title: String,
    val detail: String,
    val earned: Boolean
)

internal fun buildFactualProfileBadges(
    ins: dev.ikna.data.repo.ProfileInsights?,
    zone: ZoneId,
    dateFmt: DateTimeFormatter
): List<FactualProfileBadge> {
    if (ins == null) return emptyList()
    fun dateOrEmpty(ms: Long?) = ms?.let { dateFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }.orEmpty()
    val pending = S.t("prof.badge_unearned")
    return listOf(
        FactualProfileBadge(
            kind = ProfileBadgeKind.FIRST_SESSION,
            title = S.t("prof.milestone_first"),
            detail = dateOrEmpty(ins.memberSinceMs).ifBlank { pending },
            earned = ins.memberSinceMs != null
        ),
        FactualProfileBadge(
            kind = ProfileBadgeKind.HUNDRED_ANSWERS,
            title = S.t("prof.milestone_hundred"),
            detail = if (ins.totalAnswersAllTime >= 100) {
                dateOrEmpty(ins.hundredthAnswerAtMs).ifBlank { "${ins.totalAnswersAllTime}" }
            } else {
                "${ins.totalAnswersAllTime}/100"
            },
            earned = ins.totalAnswersAllTime >= 100
        ),
        FactualProfileBadge(
            kind = ProfileBadgeKind.FIRST_MATURE,
            title = S.t("prof.milestone_mature"),
            detail = if (ins.matureCards > 0 || ins.firstMatureAtMs != null) {
                dateOrEmpty(ins.firstMatureAtMs).ifBlank { "${ins.matureCards}" }
            } else {
                pending
            },
            earned = ins.matureCards > 0 || ins.firstMatureAtMs != null
        ),
        FactualProfileBadge(
            kind = ProfileBadgeKind.MULTILINGUAL,
            title = S.t("prof.badge_multilingual"),
            detail = if (ins.languages.size >= 2) {
                ins.languages.joinToString(" · ") { it.lang.uppercase(Locale.ROOT) }
            } else {
                "${ins.languages.size}/2"
            },
            earned = ins.languages.size >= 2
        ),
        FactualProfileBadge(
            kind = ProfileBadgeKind.FIVE_HUNDRED_ANSWERS,
            title = S.t("prof.milestone_five_hundred"),
            detail = if (ins.totalAnswersAllTime >= 500) {
                dateOrEmpty(ins.fiveHundredthAnswerAtMs).ifBlank { "${ins.totalAnswersAllTime}" }
            } else {
                "${ins.totalAnswersAllTime}/500"
            },
            earned = ins.totalAnswersAllTime >= 500
        )
    )
}

@Composable
private fun ProfileHeaderBadgeTray(earnedBadges: List<FactualProfileBadge>) {
    val colors = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .background(colors.surface.copy(alpha = 0.85f), pillShape)
            .border(1.dp, colors.outline.copy(alpha = 0.40f), pillShape)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        earnedBadges.take(5).forEach { badge ->
            ProfileBadgeEmblem(
                kind = badge.kind,
                earned = true,
                size = 16.dp
            )
        }
    }
}

@Composable
private fun ProfileBadgeEmblem(
    kind: ProfileBadgeKind,
    earned: Boolean,
    size: Dp = 20.dp
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (earned) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.38f)
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            ProfileBadgeKind.FIRST_SESSION -> {
                // Compass / seed node
                drawCircle(color = tint, radius = w * 0.38f, style = stroke)
                drawCircle(color = tint, radius = w * 0.13f)
            }
            ProfileBadgeKind.HUNDRED_ANSWERS -> {
                // Crisp cadence diamond
                val diamond = Path().apply {
                    moveTo(cx, h * 0.10f)
                    lineTo(w * 0.90f, cy)
                    lineTo(cx, h * 0.90f)
                    lineTo(w * 0.10f, cy)
                    close()
                }
                drawPath(diamond, color = tint, style = stroke)
                if (earned) {
                    drawCircle(color = tint, radius = w * 0.12f)
                }
            }
            ProfileBadgeKind.FIRST_MATURE -> {
                // Deep memory hexagonal prism
                val hex = Path().apply {
                    moveTo(cx, h * 0.08f)
                    lineTo(w * 0.86f, h * 0.29f)
                    lineTo(w * 0.86f, h * 0.71f)
                    lineTo(cx, h * 0.92f)
                    lineTo(w * 0.14f, h * 0.71f)
                    lineTo(w * 0.14f, h * 0.29f)
                    close()
                }
                drawPath(hex, color = tint, style = stroke)
                drawLine(tint, Offset(cx, h * 0.30f), Offset(cx, h * 0.70f), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
            }
            ProfileBadgeKind.MULTILINGUAL -> {
                // Twin interlocked orbits
                drawCircle(color = tint, radius = w * 0.28f, center = Offset(w * 0.37f, cy), style = stroke)
                drawCircle(color = tint, radius = w * 0.28f, center = Offset(w * 0.63f, cy), style = stroke)
            }
            ProfileBadgeKind.FIVE_HUNDRED_ANSWERS -> {
                // Archive lattice (3x3 geometric frame)
                val pad = w * 0.16f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(pad, pad),
                    size = Size(w - pad * 2f, h - pad * 2f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                drawLine(tint, Offset(cx, pad), Offset(cx, h - pad), strokeWidth = 1.2.dp.toPx())
                drawLine(tint, Offset(pad, cy), Offset(w - pad, cy), strokeWidth = 1.2.dp.toPx())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileBadgesSection(
    badges: List<FactualProfileBadge>
) {
    val colors = MaterialTheme.colorScheme
    val earnedCount = badges.count { it.earned }
    val shape = RoundedCornerShape(10.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = S.t("prof.badges_title"),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground
            )
            Text(
                text = "$earnedCount/${badges.size}",
                style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                color = colors.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            badges.forEach { badge ->
                Row(
                    modifier = Modifier
                        .background(
                            color = if (badge.earned) colors.primary.copy(alpha = 0.08f) else colors.background.copy(alpha = 0.42f),
                            shape = shape
                        )
                        .border(
                            width = 1.dp,
                            color = if (badge.earned) colors.primary.copy(alpha = 0.34f) else colors.outline.copy(alpha = 0.26f),
                            shape = shape
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileBadgeEmblem(
                        kind = badge.kind,
                        earned = badge.earned,
                        size = 18.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = badge.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (badge.earned) FontWeight.Medium else FontWeight.Normal,
                            color = if (badge.earned) colors.onBackground else colors.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = badge.detail,
                            style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                            color = if (badge.earned) colors.primary.copy(alpha = 0.85f) else colors.onSurfaceVariant.copy(alpha = 0.55f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

private fun cleanLanguageDisplayName(code: String): String = when (code.trim().lowercase(Locale.ROOT)) {
    "en" -> "English"
    "ru" -> "Русский"
    "de" -> "Deutsch"
    "es" -> "Español"
    "fr" -> "Français"
    "pl" -> "Polski"
    "pt" -> "Português"
    "zh" -> "中文"
    "ja" -> "日本語"
    "ko" -> "한국어"
    "it" -> "Italiano"
    "tr" -> "Türkçe"
    "uk" -> "Українська"
    "ar" -> "العربية"
    "nl" -> "Nederlands"
    "sv" -> "Svenska"
    "cs" -> "Čeština"
    "fi" -> "Suomi"
    "el" -> "Ελληνικά"
    "he" -> "עברית"
    "hi" -> "हिन्दी"
    "vi" -> "Tiếng Việt"
    else -> code.trim().uppercase(Locale.ROOT)
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
    val shape = RoundedCornerShape(999.dp)
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
                val code = item.lang.uppercase(Locale.ROOT)
                val name = cleanLanguageDisplayName(item.lang)
                Row(
                    modifier = Modifier
                        .background(colors.background.copy(alpha = 0.55f), shape)
                        .border(1.dp, colors.outline.copy(alpha = 0.35f), shape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = code,
                        style = iknaNumberStyle(MaterialTheme.typography.labelMedium, strong = true),
                        color = colors.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (name != code) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onBackground,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
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
