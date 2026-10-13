package dev.ikna.data.export

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app must be able to call the shared export boundary, not just shared tests. */
class ExportDocumentApiTest {
    @Test fun appModuleCanWriteAndObserveExportFailure() {
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        writeExportDocument({ "disposable-document" }, { output }) { it.write("dev-history") }
        assertEquals("dev-history", output.toString("UTF-8"))
        assertTrue(closed)
        val failure = runCatching {
            writeExportDocument({ "disposable-document" }, { null }) { it.write("dev-history") }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
    }
}
