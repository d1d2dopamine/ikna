package dev.ikna.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDeckSearchTest {
    @Test
    fun `spaces are normalised and the query is wrapped`() {
        assertEquals(
            LocalSearchTerms("take care", "%take care%", "take care%"),
            localSearchTerms("  take   care  ")
        )
    }

    @Test
    fun `like wildcards are searched as literal characters`() {
        assertEquals("%50\\%\\_off%", localSearchTerms("50%_off")?.contains)
        assertEquals("%a\\\\b%", localSearchTerms("a\\b")?.contains)
    }

    @Test
    fun `one character cannot trigger a collection scan`() {
        assertNull(localSearchTerms(""))
        assertNull(localSearchTerms("я"))
    }
}
