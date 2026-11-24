package dev.ikna.ui.settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaAppearanceVariant
import dev.ikna.data.prefs.MANUAL_LOAD_MAX
import dev.ikna.data.prefs.MANUAL_LOAD_MIN
import dev.ikna.data.prefs.MANUAL_LOAD_STEP
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import dev.ikna.ui.theme.iknaNumberStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

fun iknaSettingsSectionGlyph(sectionId: String): IknaGlyph = when (sectionId) {
    "load" -> IknaGlyph.SLIDERS
    "look" -> IknaGlyph.PALETTE
    "language" -> IknaGlyph.GLOBE
    "speech" -> IknaGlyph.SOUND
    "font" -> IknaGlyph.FONT
    "keys" -> IknaGlyph.KEYBOARD
    "reminder" -> IknaGlyph.BELL
    "update" -> IknaGlyph.UPDATE
    "data" -> IknaGlyph.DATA
    "advanced" -> IknaGlyph.ADVANCED
    else -> IknaGlyph.GEAR
}

/** Select the keyed section at the reading line in the upper part of the viewport. */
internal fun resolveActiveSettingsSectionId(
    sectionIds: List<String>,
    visibleItems: List<LazyListItemInfo>,
    viewportStart: Int,
    viewportEnd: Int,
    totalItems: Int,
    canScrollForward: Boolean
): String {
    val firstSection = sectionIds.firstOrNull().orEmpty()
    val lastSection = sectionIds.lastOrNull() ?: return firstSection
    val lastVisible = visibleItems.lastOrNull() ?: return firstSection
    if (!canScrollForward && (lastVisible.key == lastSection || lastVisible.index == totalItems - 1)) {
        return lastSection
    }

    val anchor = viewportStart + (viewportEnd - viewportStart) * 0.24f
    val visibleSections = visibleItems.filter {
        val key = it.key
        key is String && key in sectionIds && it.offset < viewportEnd && it.offset + it.size > viewportStart
    }
    return (visibleSections.lastOrNull { it.offset <= anchor } ?: visibleSections.firstOrNull())
        ?.key as? String ?: firstSection
}

@Composable
fun IknaSettingsJumpRow(
    sections: List<Pair<String, String>>,
    listState: LazyListState,
    animations: Boolean,
    settled: Boolean,
    edgePadding: Dp = 20.dp,
    onJump: (String) -> Unit
) {
    var pinnedId by remember(sections) { mutableStateOf<String?>(null) }
    var jumpInFlight by remember(sections) { mutableStateOf(false) }

    val scrollActiveId by remember(listState, sections) {
        val sectionIds = sections.map { it.first }
        derivedStateOf {
            val layout = listState.layoutInfo
            resolveActiveSettingsSectionId(
                sectionIds, layout.visibleItemsInfo, layout.viewportStartOffset,
                layout.viewportEndOffset, layout.totalItemsCount, listState.canScrollForward
            )
        }
    }
    val verticalScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    LaunchedEffect(verticalScrolling) {
        if (verticalScrolling) {
            if (!jumpInFlight) pinnedId = null
        } else if (jumpInFlight) {
            jumpInFlight = false
        }
    }
    val activeId = pinnedId?.takeIf { id ->
        jumpInFlight || listState.layoutInfo.visibleItemsInfo.any { it.key == id }
    } ?: scrollActiveId

    IknaJumpRow(
        sections = sections,
        activeId = activeId,
        animations = animations,
        settled = settled,
        verticalScrolling = verticalScrolling,
        edgePadding = edgePadding,
        onJump = { target ->
            pinnedId = target
            jumpInFlight = true
            onJump(target)
        }
    )
}

@Composable
fun IknaJumpRow(
    sections: List<Pair<String, String>>,
    activeId: String,
    animations: Boolean,
    settled: Boolean,
    verticalScrolling: Boolean,
    edgePadding: Dp = 20.dp,
    onJump: (String) -> Unit
) {
    val row = rememberScrollState()

    // Where each label sits inside the row, measured the way the sections below
    // measure themselves. Centring needs the width of the label as well as its
    // position, so this stores the span rather than the left edge.
    val spots = remember { mutableStateMapOf<String, IntRange>() }
    var rowWidth by remember { mutableStateOf(0) }
    // The viewport can stay the same while a language/font change moves the
    // active label. Observe its measured span outside the effect so the final
    // layout, including the first measured label, triggers recentring.
    val activeSpot = spots[activeId]
    val maxScroll = row.maxValue

    // The turn. Not a jump to the edge: the label of the section being read ends
    // up in the middle, which is the only position that reads as "you are here"
    // rather than "here is a list".
    LaunchedEffect(activeId, rowWidth, activeSpot, maxScroll, animations, settled, verticalScrolling) {
        // Do not run a second scroll animation while the main list is moving.
        // When it settles, verticalScrolling becomes false and this effect
        // recentres the final active label once.
        if (!settled || verticalScrolling || rowWidth == 0) return@LaunchedEffect
        val spot = activeSpot ?: return@LaunchedEffect
        val middle = spot.first + (spot.last - spot.first) / 2
        val target = (middle - rowWidth / 2).coerceIn(0, maxScroll)
        if (animations) {
            row.animateScrollTo(
                target,
                animationSpec = tween(
                    durationMillis = Motion.sectionScrollDurationMillis,
                    easing = LinearOutSlowInEasing
                )
            )
        } else {
            row.scrollTo(target)
        }
    }

    val targetLeft = activeSpot?.first?.toFloat() ?: 0f
    val targetRight = activeSpot?.last?.toFloat() ?: 0f
    val hasSpot = activeSpot != null
    val animLeft = remember { Animatable(0f) }
    val animRight = remember { Animatable(0f) }
    var indicatorReady by remember { mutableStateOf(false) }

    LaunchedEffect(targetLeft, targetRight, hasSpot, animations) {
        if (!hasSpot) return@LaunchedEffect
        if (!indicatorReady || !animations) {
            animLeft.snapTo(targetLeft)
            animRight.snapTo(targetRight)
            indicatorReady = true
        } else {
            val spec = tween<Float>(
                durationMillis = 220,
                easing = FastOutSlowInEasing
            )
            launch { animLeft.animateTo(targetLeft, spec) }
            launch { animRight.animateTo(targetRight, spec) }
        }
    }

    val colors = MaterialTheme.colorScheme
    val rounded = LocalIknaAppearanceVariant.current == IknaAppearanceVariant.ROUNDED
    val sound = LocalIknaSoundFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowWidth = it.size.width }
            .horizontalScroll(row)
    ) {
        Box {
            Row(
                modifier = Modifier.padding(horizontal = edgePadding),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sections.forEach { jump ->
                    val here = jump.first == activeId
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    val pressed by interaction.collectIsPressedAsState()
                    val scale = iknaPressScale(pressed, animations, pressedScale = 0.96f)
                    val labelColor by animateColorAsState(
                        targetValue = when {
                            here -> colors.onBackground
                            hovered -> colors.onBackground.copy(alpha = 0.85f)
                            else -> colors.onSurfaceVariant
                        },
                        animationSpec = if (animations) tween(Motion.controlChangeDurationMillis) else snap(),
                        label = "jump-tab-label"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = when {
                            here -> colors.primary
                            hovered -> colors.onBackground.copy(alpha = 0.80f)
                            else -> colors.onSurfaceVariant.copy(alpha = 0.78f)
                        },
                        animationSpec = if (animations) tween(Motion.controlChangeDurationMillis) else snap(),
                        label = "jump-tab-icon"
                    )
                    Row(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                val x = coords.parentLayoutCoordinates
                                    ?.localPositionOf(coords, Offset.Zero)
                                    ?.x
                                    ?: 0f
                                val left = x.roundToInt()
                                spots[jump.first] = left..(left + coords.size.width)
                            }
                            .heightIn(min = 42.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .semantics { selected = here }
                            .hoverable(interaction)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab
                            ) {
                                sound.play(IknaSoundCue.TAP)
                                onJump(jump.first)
                            }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        IknaGlyphIcon(
                            glyph = iknaSettingsSectionGlyph(jump.first),
                            color = iconColor,
                            size = 14.dp
                        )
                        Text(
                            text = S.t(jump.second),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (here) FontWeight.SemiBold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1
                        )
                    }
                }
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                if (indicatorReady && animRight.value > animLeft.value) {
                    val barHeight = 2.5.dp.toPx()
                    val corner = CornerRadius(if (rounded) barHeight / 2f else 0f)
                    drawRoundRect(
                        color = colors.primary,
                        topLeft = Offset(animLeft.value, size.height - barHeight),
                        size = Size(animRight.value - animLeft.value, barHeight),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

@Composable
fun IknaSettingsStepper(
    value: Int,
    enabled: Boolean,
    min: Int = MANUAL_LOAD_MIN,
    max: Int = MANUAL_LOAD_MAX,
    step: Int = MANUAL_LOAD_STEP,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IknaWideButton(
            label = "-",
            modifier = Modifier.width(72.dp),
            height = 52.dp,
            enabled = enabled && value > min,
            onClick = { onChange((value - step).coerceAtLeast(min)) }
        )
        Text(
            text = value.toString(),
            style = iknaNumberStyle(MaterialTheme.typography.displaySmall),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
        IknaWideButton(
            label = "+",
            modifier = Modifier.width(72.dp),
            height = 52.dp,
            enabled = enabled && value < max,
            onClick = { onChange((value + step).coerceAtMost(max)) }
        )
    }
}

@Composable
fun IknaSettingsSection(
    title: String,
    subtitle: String?,
    icon: IknaGlyph? = null,
    content: @Composable () -> Unit
) {
    Spacer(Modifier.height(Space.xl))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            IknaGlyphIcon(
                glyph = icon,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                size = 18.dp
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
    val motionEnabled = LocalIknaMotionEnabled.current
    Column(
        modifier = Modifier.animateContentSize(
            animationSpec = if (motionEnabled) tween(
                durationMillis = Motion.contentChangeDurationMillis,
                easing = LinearOutSlowInEasing
            ) else snap()
        ).iknaInspect("IknaSettingsSection[$title]")
    ) {
        content()
    }
}

@Composable
fun IknaSettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .iknaInspect("IknaSettingsToggleRow[$title]"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // The row's own title is what the switch is called. A screen reader
        // treats the switch as a separate stop, so without this it would be
        // announced as an anonymous "switch, on" after the text has been read.
        IknaToggle(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            label = title
        )
    }
}
