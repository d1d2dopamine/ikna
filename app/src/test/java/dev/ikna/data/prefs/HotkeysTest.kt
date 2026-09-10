package dev.ikna.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeysTest {

    @Test
    fun `defaults keep arrows for the two visible actions`() {
        val bindings = HotkeyBindings.decode(DEFAULT_HOTKEYS)
        assertEquals("LEFT", bindings[HotkeyAction.MISS]?.encoded)
        assertEquals("RIGHT", bindings[HotkeyAction.KNOW]?.encoded)
        assertEquals(HotkeyAction.entries.size, bindings.values.distinct().size)
    }

    @Test
    fun `one ordinary key and no more than three keys are accepted`() {
        assertEquals("K", HotkeyChord.parse("K")?.encoded)
        assertEquals("CTRL+SHIFT+K", HotkeyChord.parse("shift+ctrl+k")?.encoded)
        assertNull(HotkeyChord.parse(""))
        assertNull(HotkeyChord.parse("CTRL+ALT"))
        assertNull(HotkeyChord.parse("CTRL+ALT+SHIFT+K"))
    }

    @Test
    fun `one malformed imported entry does not discard the other bindings`() {
        val bindings = HotkeyBindings.decode("miss=CTRL+ALT;know=CTRL+K;future=F8")
        assertEquals("LEFT", bindings[HotkeyAction.MISS]?.encoded)
        assertEquals("CTRL+K", bindings[HotkeyAction.KNOW]?.encoded)
        assertEquals("SPACE", bindings[HotkeyAction.REVEAL]?.encoded)
    }

    @Test
    fun `replacement survives canonical encoding`() {
        val chord = requireNotNull(HotkeyChord.parse("ALT+J"))
        val encoded = HotkeyBindings.replace(DEFAULT_HOTKEYS, HotkeyAction.UNDO, chord)
        val restored = HotkeyBindings.decode(encoded)
        assertEquals("ALT+J", restored[HotkeyAction.UNDO]?.encoded)
        assertTrue(HotkeyAction.entries.all { encoded.contains("${it.storedName}=") })
    }

    @Test
    fun `obsolete manual grade entries in an older backup are ignored`() {
        val bindings = HotkeyBindings.decode(
            "miss=LEFT;know=RIGHT;reveal=SPACE;again=1;hard=2;good=3;easy=4;undo=Z"
        )
        assertEquals(setOf("miss", "know", "reveal", "undo"),
            HotkeyAction.entries.map { it.storedName }.toSet())
        assertEquals(4, bindings.size)
    }

    @Test
    fun `conflicts are reported without changing either action`() {
        val bindings = HotkeyBindings.decode(DEFAULT_HOTKEYS)
        val left = requireNotNull(bindings[HotkeyAction.MISS])
        assertEquals(
            HotkeyAction.MISS,
            HotkeyBindings.conflictingAction(bindings, HotkeyAction.KNOW, left)
        )
        assertNull(HotkeyBindings.conflictingAction(bindings, HotkeyAction.MISS, left))
    }
}
