@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package dev.ikna.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import dev.ikna.data.prefs.HotkeyAction
import dev.ikna.data.prefs.HotkeyChord

internal enum class HotkeyCaptureProblem { WAITING_FOR_MAIN_KEY, TOO_MANY_KEYS }

internal data class HotkeyCaptureResult(
    val chord: HotkeyChord? = null,
    val problem: HotkeyCaptureProblem? = null
)

/** Turns a real key press into the portable chord kept in SettingsStore. */
internal fun captureHotkey(event: KeyEvent): HotkeyCaptureResult {
    val main = mainKeyToken(event)
        ?: return HotkeyCaptureResult(problem = HotkeyCaptureProblem.WAITING_FOR_MAIN_KEY)
    val tokens = buildList {
        if (event.isCtrlPressed) add("CTRL")
        if (event.isAltPressed) add("ALT")
        if (event.isShiftPressed) add("SHIFT")
        if (event.isMetaPressed) add("META")
        add(main)
    }
    if (tokens.size > 3) {
        return HotkeyCaptureResult(problem = HotkeyCaptureProblem.TOO_MANY_KEYS)
    }
    val chord = HotkeyChord.of(tokens)
        ?: return HotkeyCaptureResult(problem = HotkeyCaptureProblem.WAITING_FOR_MAIN_KEY)
    return HotkeyCaptureResult(chord = chord)
}

internal fun hotkeyAction(
    event: KeyEvent,
    bindings: Map<HotkeyAction, HotkeyChord>
): HotkeyAction? {
    val chord = captureHotkey(event).chord ?: return null
    // These must continue to reach Shell.handleWindowKey even if a malformed
    // imported backup contains one of them.
    if (isReservedGlobalHotkey(chord)) return null
    return bindings.entries.firstOrNull { it.value == chord }?.key
}

internal fun isReservedGlobalHotkey(chord: HotkeyChord): Boolean = chord.encoded in setOf(
    "ESCAPE",
    "F1",
    "F11",
    "CTRL+1",
    "CTRL+2",
    "CTRL+3",
    "CTRL+4",
    "CTRL+COMMA",
    "CTRL+Q"
)

internal fun hotkeyDisplay(chord: HotkeyChord): String = chord.tokens.joinToString(" + ") { token ->
    when (token) {
        "CTRL" -> "Ctrl"
        "ALT" -> "Alt"
        "SHIFT" -> "Shift"
        "META" -> "Meta"
        "LEFT" -> "←"
        "RIGHT" -> "→"
        "UP" -> "↑"
        "DOWN" -> "↓"
        "SPACE" -> "Space"
        "ENTER" -> "Enter"
        "BACKSPACE" -> "Backspace"
        "DELETE" -> "Delete"
        "TAB" -> "Tab"
        "ESCAPE" -> "Esc"
        "COMMA" -> ","
        "PERIOD" -> "."
        "SLASH" -> "/"
        "BACKSLASH" -> "\\"
        "SEMICOLON" -> ";"
        "APOSTROPHE" -> "'"
        "LEFT_BRACKET" -> "["
        "RIGHT_BRACKET" -> "]"
        "MINUS" -> "-"
        "EQUALS" -> "="
        "GRAVE" -> "`"
        else -> token.removePrefix("CHAR_").toIntOrNull(16)?.let { codePoint ->
            runCatching { Character.toChars(codePoint).concatToString() }.getOrNull()
        } ?: token
    }
}

private fun mainKeyToken(event: KeyEvent): String? = when (event.key) {
    Key.DirectionLeft -> "LEFT"
    Key.DirectionRight -> "RIGHT"
    Key.DirectionUp -> "UP"
    Key.DirectionDown -> "DOWN"
    Key.Spacebar -> "SPACE"
    Key.Enter, Key.NumPadEnter -> "ENTER"
    Key.Backspace -> "BACKSPACE"
    Key.Delete -> "DELETE"
    Key.Tab -> "TAB"
    Key.Escape -> "ESCAPE"
    Key.F1 -> "F1"
    Key.F2 -> "F2"
    Key.F3 -> "F3"
    Key.F4 -> "F4"
    Key.F5 -> "F5"
    Key.F6 -> "F6"
    Key.F7 -> "F7"
    Key.F8 -> "F8"
    Key.F9 -> "F9"
    Key.F10 -> "F10"
    Key.F11 -> "F11"
    Key.F12 -> "F12"
    Key.A -> "A"
    Key.B -> "B"
    Key.C -> "C"
    Key.D -> "D"
    Key.E -> "E"
    Key.F -> "F"
    Key.G -> "G"
    Key.H -> "H"
    Key.I -> "I"
    Key.J -> "J"
    Key.K -> "K"
    Key.L -> "L"
    Key.M -> "M"
    Key.N -> "N"
    Key.O -> "O"
    Key.P -> "P"
    Key.Q -> "Q"
    Key.R -> "R"
    Key.S -> "S"
    Key.T -> "T"
    Key.U -> "U"
    Key.V -> "V"
    Key.W -> "W"
    Key.X -> "X"
    Key.Y -> "Y"
    Key.Z -> "Z"
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    Key.Comma -> "COMMA"
    else -> printableToken(event.utf16CodePoint)
}

private fun printableToken(codePoint: Int): String? {
    if (codePoint <= 32 || codePoint > Character.MAX_CODE_POINT || Character.isISOControl(codePoint)) {
        return null
    }
    val text = runCatching { Character.toChars(codePoint).concatToString() }.getOrNull()
        ?: return null
    val normalized = when (text) {
        ",", "<" -> "COMMA"
        ".", ">" -> "PERIOD"
        "/", "?" -> "SLASH"
        "\\", "|" -> "BACKSLASH"
        ";", ":" -> "SEMICOLON"
        "'", "\"" -> "APOSTROPHE"
        "[", "{" -> "LEFT_BRACKET"
        "]", "}" -> "RIGHT_BRACKET"
        "-", "_" -> "MINUS"
        "=", "+" -> "EQUALS"
        "`", "~" -> "GRAVE"
        "!" -> "1"
        "@" -> "2"
        "#" -> "3"
        "\$" -> "4"
        "%" -> "5"
        "^" -> "6"
        "&" -> "7"
        "*" -> "8"
        "(" -> "9"
        ")" -> "0"
        else -> null
    }
    if (normalized != null) return normalized
    val upper = text.uppercase()
    return if (upper.length == 1 && upper.single().isLetterOrDigit() && upper.single().code < 128) {
        upper
    } else {
        "CHAR_${codePoint.toString(16).uppercase()}"
    }
}
