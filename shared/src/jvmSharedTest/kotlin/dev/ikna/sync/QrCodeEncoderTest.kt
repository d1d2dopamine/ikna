package dev.ikna.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QrCodeEncoderTest {

    @Test
    fun encodesShortAndCompactPairingPayloadsWithValidFinderPatterns() {
        val sample = "ikna-pair:1:eJyrVkrLz1eyUsoqTVWyUqqOUUqvVNKpqQUAU0gHfQ"
        val matrix = QrCodeEncoder.encode(sample)
        assertTrue(matrix.size in 21..177)
        assertEquals(0, (matrix.size - 17) % 4)
        // Top-left 7x7 finder outer border is dark, inner 5x5 ring is light, center 3x3 is dark.
        assertTrue(matrix[0, 0])
        assertTrue(matrix[6, 6])
        assertTrue(!matrix[1, 1])
        assertTrue(matrix[3, 3])
        // Top-right and bottom-left finder centers.
        assertTrue(matrix[matrix.size - 4, 3])
        assertTrue(matrix[3, matrix.size - 4])
    }

    @Test
    fun compactInvitationRoundTripsThroughDeflatePrefix() {
        val pemBody = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtokenSampleLine123456\n".repeat(16)
        val raw = """{"version":1,"host":"192.168.1.42","port":45123,"certificate":"-----BEGIN CERTIFICATE-----\n$pemBody-----END CERTIFICATE-----","binding":{"groupId":"g1","epoch":"e1","freshStart":false},"secret":"0123456789abcdef0123456789abcdef0123456789a","expiresAtMillis":9999999999999}"""
        val compact = PairedLanTransport.encodeCompactInvitation(raw)
        assertTrue(compact.startsWith(PairedLanTransport.COMPACT_INVITATION_PREFIX))
        assertTrue(compact.length < raw.length, "Compact encoding should shorten full PEM JSON invitations")
        val matrix = QrCodeEncoder.encode(compact)
        assertTrue(matrix.size in 21..177)
    }

    @Test
    fun rejectsEmptyPayload() {
        assertFailsWith<IllegalArgumentException> {
            QrCodeEncoder.encode("")
        }
    }

    @Test
    fun levelMEncodesCompactV2PayloadIntoSmallCrispMatrix() {
        val sample = "ikna-pair:2:eJyrVkrLz1eyUsoqTVWyUqqOUUqvVNKpqQUAU0gHfQ"
        val matrixM = QrCodeEncoder.encode(sample, QrErrorCorrection.M)
        val matrixH = QrCodeEncoder.encode(sample, QrErrorCorrection.H)
        assertTrue(matrixM.size in 21..177)
        assertTrue(matrixM.size <= matrixH.size, "Level M should be at least as compact as Level H")
        assertTrue(matrixM[0, 0] && matrixM[6, 6] && !matrixM[1, 1] && matrixM[3, 3])
        assertTrue(matrixM[matrixM.size - 4, 3] && matrixM[3, matrixM.size - 4])
    }
}
