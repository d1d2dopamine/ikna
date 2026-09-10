package dev.ikna.ui.settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.ikna.data.prefs.MANUAL_LOAD_MIN
import dev.ikna.data.prefs.MANUAL_LOAD_MAX
import dev.ikna.data.prefs.MANUAL_LOAD_STEP
import kotlin.math.roundToInt

@Composable
fun IknaSettingsJumpRow(
    sections: List<Pair<String, String>>,
    listState: LazyListState,
    animations: Boolean,
    settled: Boolean,
    onJump: (String) -> Unit
) {
    val activeId by remember(listState) {
        derivedStateOf {
            sections[listState.firstVisibleItemIndex.coerceIn(0, sections.lastIndex)].first
        }
    }
    val verticalScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    IknaJumpRow(
        sections = sections,
        activeId = activeId,
        animations = animations,
        settled = settled,
        verticalScrolling = verticalScrolling,
        onJump = onJump
    )
}

@Composable
fun IknaJumpRow(
    sections: List<Pair<String, String>>,
    activeId: String,
    animations: Boolean,
    settled: Boolean,
    verticalScrolling: Boolean,
    onJump: (String) -> Unit
) {
    val row = rememberScrollState()

    // Where each label sits inside the row, measured the way the sections below
    // measure themselves. Centring needs the width of the label as well as its
    // position, so this stores the span rather than the left edge.
    val spots = remember { mutableStateMapOf<String, IntRange>() }
    var rowWidth by remember { mutableStateOf(0) }

    // The turn. Not a jump to the edge: the label of the section being read ends
    // up in the middle, which is the only position that reads as "you are here"
    // rather than "here is a list".
    LaunchedEffect(activeId, rowWidth, animations, settled, verticalScrolling) {
        // Do not run a second scroll animation while the main list is moving.
        // When it settles, verticalScrolling becomes false and this effect
        // recentres the final active label once.
        if (!settled || verticalScrolling || rowWidth == 0) return@LaunchedEffect
        val spot = spots[activeId] ?: return@LaunchedEffect
        val middle = spot.first + (spot.last - spot.first) / 2
        val target = (middle - rowWidth / 2).coerceIn(0, row.maxValue)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowWidth = it.size.width }
            .horizontalScroll(row)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        sections.forEach { jump ->
            val here = jump.first == activeId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.onGloballyPositioned { coords ->
                    val x = coords.parentLayoutCoordinates
                        ?.localPositionOf(coords, Offset.Zero)
                        ?.x
                        ?: 0f
                    val left = x.roundToInt()
                    spots[jump.first] = left..(left + coords.size.width)
                }
            ) {
                IknaTextButton(
                    label = S.t(jump.second),
                    onClick = { onJump(jump.first) },
                    color = if (here) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(2.dp)
                                .background(
                                    if (here) MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (index == 1) 1f else 0.52f
                                    ) else Color.Transparent
                                )
                        )
                    }
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
            style = MaterialTheme.typography.displaySmall,
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
    content: @Composable () -> Unit
) {
    Spacer(Modifier.height(24.dp))
    IknaRule(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
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
            onCheckedChange = onCheckedChange,
            label = title
        )
    }
}
