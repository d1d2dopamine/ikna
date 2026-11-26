package dev.ikna.sync

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies our pure-Kotlin QR encoder output against the actual ZXing decoder shipped in Android,
 * covering both Level-M (compact v2 pairing QR) and Level-H matrices.
 */
class QrCodeZxingDecodeTest {

    private fun decodeWithZxing(matrix: QrMatrix): String {
        val quiet = 4
        val scale = 4
        val dim = (matrix.size + quiet * 2) * scale
        val pixels = IntArray(dim * dim) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    val py0 = (y + quiet) * scale
                    val px0 = (x + quiet) * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (py0 + dy) * dim + px0
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = 0xFF000000.toInt()
                        }
                    }
                }
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(dim, dim, pixels)))
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8",
        )
        return QRCodeReader().decode(bitmap, hints).text
    }

    @Test
    fun levelMAndLevelHRoundTripRealCompactV2InvitationThroughZxing() {
        val dir = Files.createTempDirectory("ikna-qr-zxing-").toFile()
        val transport = PairedLanTransport(
            dir,
            HistoryBinding("group-1", "epoch-1"),
            object : PairedLanTransport.PayloadCallbacks {
                override suspend fun snapshot(): ByteArray = ByteArray(0)
                override suspend fun receive(payload: ByteArray) = Unit
            }
        )
        try {
            transport.start()
            val rawInvite = transport.createInvitation("192.168.101.6")
            val qrV2 = PairedLanTransport.encodeQrInvitation(rawInvite)

            val matrixM = QrCodeEncoder.encode(qrV2, QrErrorCorrection.M)
            val matrixH = QrCodeEncoder.encode(qrV2, QrErrorCorrection.H)
            assertTrue("Level M must stay compact for phone-to-monitor scans (size=${matrixM.size})", matrixM.size <= 69)
            assertEquals(qrV2, decodeWithZxing(matrixM))
            assertEquals(qrV2, decodeWithZxing(matrixH))

            val parsed = PairedLanTransport.parseInvitation(decodeWithZxing(matrixM))
            assertEquals("192.168.101.6", parsed.host)
            assertEquals(64, parsed.certPin.length)
        } finally {
            transport.close()
            dir.deleteRecursively()
        }
    }
}
