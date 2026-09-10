package dev.ikna.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.HotkeyAction
import dev.ikna.data.prefs.HotkeyBindings
import dev.ikna.data.prefs.HotkeyChord
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.LocalIknaMotionEnabled
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space

private val PRIMARY_HOTKEY_ACTIONS = listOf(HotkeyAction.MISS, HotkeyAction.KNOW)
private val EXTRA_HOTKEY_ACTIONS = listOf(
    HotkeyAction.REVEAL,
    HotkeyAction.AGAIN,
    HotkeyAction.HARD,
    HotkeyAction.GOOD,
    HotkeyAction.EASY,
    HotkeyAction.UNDO
)

private data class HotkeyUndo(val action: HotkeyAction, val chord: HotkeyChord)

/** Desktop-only editor: it captures key events; it never accepts typed text. */
@Composable
internal fun HotkeySettingsContent(
    settings: IknaSettings,
    palette: IknaPalette,
    onSetHotkey: (HotkeyAction, HotkeyChord) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf<HotkeyAction?>(null) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<HotkeyUndo?>(null) }
    var localEncoded by remember { mutableStateOf(settings.hotkeys) }
    LaunchedEffect(settings.hotkeys) { localEncoded = settings.hotkeys }
    val bindings = remember(localEncoded) { HotkeyBindings.decode(localEncoded) }

    fun begin(action: HotkeyAction) {
        capturing = action
        errorKey = null
    }

    fun accept(action: HotkeyAction, chord: HotkeyChord) {
        when {
            isReservedGlobalHotkey(chord) -> errorKey = "keys.017"
            HotkeyBindings.conflictingAction(bindings, action, chord) != null -> errorKey = "keys.016"
            else -> {
                val previous = requireNotNull(bindings[action])
                if (previous != chord) {
                    localEncoded = HotkeyBindings.replace(localEncoded, action, chord)
                    undo = HotkeyUndo(action, previous)
                    onSetHotkey(action, chord)
                }
                capturing = null
                errorKey = null
            }
        }
    }

    fun restore(change: HotkeyUndo) {
        localEncoded = HotkeyBindings.replace(localEncoded, change.action, change.chord)
        onSetHotkey(change.action, change.chord)
        capturing = null
        errorKey = null
        undo = null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        PRIMARY_HOTKEY_ACTIONS.forEach { action ->
            HotkeyBindingRow(
                action = action,
                chord = requireNotNull(bindings[action]),
                palette = palette,
                capturing = capturing == action,
                errorKey = errorKey.takeIf { capturing == action },
                canUndo = undo?.action == action,
                onBegin = { begin(action) },
                onCancel = {
                    capturing = null
                    errorKey = null
                },
                onTooMany = { errorKey = "keys.015" },
                onWaiting = { errorKey = null },
                onAccept = { accept(action, it) },
                onUndo = { undo?.let { restore(it) } }
            )
        }
        IknaTextButton(
            label = S.t(if (expanded) "keys.013" else "keys.012"),
            onClick = {
                expanded = !expanded
                if (!expanded && capturing != null && capturing in EXTRA_HOTKEY_ACTIONS) {
                    capturing = null
                    errorKey = null
                }
            },
            modifier = Modifier.align(Alignment.Start),
            color = palette.accent
        )
        if (expanded) {
            EXTRA_HOTKEY_ACTIONS.forEach { action ->
                HotkeyBindingRow(
                    action = action,
                    chord = requireNotNull(bindings[action]),
                    palette = palette,
                    capturing = capturing == action,
                    errorKey = errorKey.takeIf { capturing == action },
                    canUndo = undo?.action == action,
                    onBegin = { begin(action) },
                    onCancel = {
                        capturing = null
                        errorKey = null
                    },
                    onTooMany = { errorKey = "keys.015" },
                    onWaiting = { errorKey = null },
                    onAccept = { accept(action, it) },
                    onUndo = { undo?.let { restore(it) } }
                )
            }
        }
    }
}

@Composable
private fun HotkeyBindingRow(
    action: HotkeyAction,
    chord: HotkeyChord,
    palette: IknaPalette,
    capturing: Boolean,
    errorKey: String?,
    canUndo: Boolean,
    onBegin: () -> Unit,
    onCancel: () -> Unit,
    onTooMany: () -> Unit,
    onWaiting: () -> Unit,
    onAccept: (HotkeyChord) -> Unit,
    onUndo: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = hotkeyActionLabel(action),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
                modifier = Modifier.weight(1f).padding(end = Space.md)
            )
            HotkeyCaptureField(
                chord = chord,
                capturing = capturing,
                palette = palette,
                onBegin = onBegin,
                onCancel = onCancel,
                onTooMany = onTooMany,
                onWaiting = onWaiting,
                onAccept = onAccept
            )
        }
        if (errorKey != null) {
            Text(
                text = S.t(errorKey),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.End).widthIn(max = 250.dp)
            )
        }
        if (canUndo) {
            IknaTextButton(
                label = S.t("keys.014"),
                onClick = onUndo,
                modifier = Modifier.align(Alignment.End),
                color = palette.accent
            )
        }
    }
}

@Composable
private fun HotkeyCaptureField(
    chord: HotkeyChord,
    capturing: Boolean,
    palette: IknaPalette,
    onBegin: () -> Unit,
    onCancel: () -> Unit,
    onTooMany: () -> Unit,
    onWaiting: () -> Unit,
    onAccept: (HotkeyChord) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val motionEnabled = LocalIknaMotionEnabled.current
    val fieldWidth by animateDpAsState(
        targetValue = if (capturing) 236.dp else 166.dp,
        animationSpec = if (motionEnabled) tween(
            durationMillis = Motion.controlChangeDurationMillis,
            easing = LinearOutSlowInEasing
        ) else snap(),
        label = "hotkey-capture-width"
    )
    LaunchedEffect(capturing) {
        if (capturing) focusRequester.requestFocus()
    }
    Box(
        modifier = Modifier
            .width(fieldWidth)
            .height(40.dp)
            .background(if (capturing) palette.accent.copy(alpha = 0.08f) else palette.panel)
            .border(if (capturing) 2.dp else 1.dp, if (capturing) palette.accent else palette.line)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (!capturing || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Escape) {
                    onCancel()
                    return@onPreviewKeyEvent true
                }
                val result = captureHotkey(event)
                when {
                    result.chord != null -> onAccept(result.chord)
                    result.problem == HotkeyCaptureProblem.TOO_MANY_KEYS -> onTooMany()
                    else -> onWaiting()
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (capturing) S.t("keys.011") else hotkeyDisplay(chord),
                style = MaterialTheme.typography.labelMedium,
                color = if (capturing) palette.accent else palette.ink,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = !capturing,
                enter = fadeIn(if (motionEnabled) tween(Motion.controlChangeDurationMillis) else snap()),
                exit = fadeOut(if (motionEnabled) tween(Motion.controlChangeDurationMillis) else snap())
            ) {
                HotkeyClearButton(palette = palette, onClick = onBegin)
            }
        }
    }
}

@Composable
private fun HotkeyClearButton(palette: IknaPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .semantics {
                role = Role.Button
                contentDescription = S.t("keys.019")
            }
            .handCursor()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(10.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(palette.muted, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = stroke)
            drawLine(palette.muted, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = stroke)
        }
    }
}

internal fun hotkeyActionLabel(action: HotkeyAction): String = S.t(
    when (action) {
        HotkeyAction.MISS -> "keys.003"
        HotkeyAction.KNOW -> "keys.004"
        HotkeyAction.REVEAL -> "keys.005"
        HotkeyAction.AGAIN -> "keys.006"
        HotkeyAction.HARD -> "keys.007"
        HotkeyAction.GOOD -> "keys.008"
        HotkeyAction.EASY -> "keys.009"
        HotkeyAction.UNDO -> "keys.010"
    }
)
