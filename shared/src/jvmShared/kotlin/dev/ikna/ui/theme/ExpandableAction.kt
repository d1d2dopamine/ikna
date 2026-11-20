package dev.ikna.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics

/** An inline action with a slot for one or more independent choice groups. */
@Composable
fun IknaExpandableAction(
    label: String,
    confirmLabel: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    choices: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val trigger = remember { FocusRequester() }
    val open = expanded && enabled
    Column(
        Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if (!open || event.key != Key.Escape || event.type != KeyEventType.KeyDown) {
                false
            } else {
                expanded = false
                trigger.requestFocus()
                true
            }
        },
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        IknaTextButton(
            label = label,
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.focusRequester(trigger).semantics {
                if (open) collapse { expanded = false; true }
                else if (enabled) expand { expanded = true; true }
            }
        )
        if (open) {
            choices()
            IknaTextButton(confirmLabel, onClick = {
                expanded = false
                trigger.requestFocus()
                onConfirm()
            })
        }
    }
}
