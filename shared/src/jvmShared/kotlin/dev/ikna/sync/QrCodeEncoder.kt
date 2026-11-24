package dev.ikna.sync

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ikna.ui.theme.iknaTileShape
import kotlin.math.abs

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code encoder (Byte mode, Error Correction Level L, Versions 1..40).
 * Zero external dependencies so Android, Windows, Linux, and F-Droid builds stay self-contained.
 */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

object QrCodeEncoder {
    // Total codewords, EC codewords per block, and block counts for Level L (versions 1..40).
    private val EC_CODEWORDS_PER_BLOCK_L = intArrayOf(
        -1,
        7, 10, 15, 20, 26, 18, 20, 24, 30, 18,
        20, 24, 26, 30, 22, 24, 28, 30, 28, 28,
        28, 28, 30, 30, 26, 28, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30, 30, 30
    )
    private val NUM_BLOCKS_L = intArrayOf(
        -1,
        1, 1, 1, 1, 1, 2, 2, 2, 2, 4,
        4, 4, 4, 4, 6, 6, 6, 6, 7, 8,
        8, 9, 9, 10, 12, 12, 12, 13, 14, 15,
        16, 17, 18, 19, 19, 20, 21, 22, 24, 25
    )

    fun encode(text: String): QrMatrix {
        val dataBytes = text.toByteArray(Charsets.UTF_8)
        require(dataBytes.isNotEmpty()) { "QR payload must not be empty" }
        val version = chooseVersion(dataBytes.size)
        val totalDataCodewords = dataCodewordsFor(version)

        val bits = BitBuffer()
        // Byte mode indicator = 0100
        bits.appendBits(0b0100, 4)
        val countBits = if (version <= 9) 8 else 16
        bits.appendBits(dataBytes.size, countBits)
        for (b in dataBytes) {
            bits.appendBits(b.toInt() and 0xFF, 8)
        }

        val capacityBits = totalDataCodewords * 8
        bits.appendBits(0, minOf(4, capacityBits - bits.length))
        bits.appendBits(0, (8 - (bits.length % 8)) % 8)
        var padByte = 0xEC
        while (bits.length < capacityBits) {
            bits.appendBits(padByte, 8)
            padByte = padByte xor (0xEC xor 0x11)
        }

        val dataCodewords = bits.toByteArray()
        val allCodewords = addEccAndInterleave(dataCodewords, version)

        val size = version * 4 + 17
        val base = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)
        drawFunctionPatterns(version, size, base, isFunction)
        drawCodewords(size, allCodewords, base, isFunction)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules = base
        for (mask in 0..7) {
            val candidate = base.copyOf()
            applyMask(size, mask, candidate, isFunction)
            drawFormatBits(size, mask, candidate, isFunction)
            val penalty = penaltyScore(size, candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestModules = candidate
            }
        }
        drawFormatBits(size, bestMask, bestModules, isFunction)
        return QrMatrix(size, bestModules)
    }

    private fun chooseVersion(byteLen: Int): Int {
        for (v in 1..40) {
            val dataBits = dataCodewordsFor(v) * 8
            val headerBits = 4 + if (v <= 9) 8 else 16
            if (headerBits + byteLen * 8 <= dataBits) return v
        }
        throw IllegalArgumentException("Payload too large for QR code ($byteLen bytes)")
    }

    private fun rawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun dataCodewordsFor(version: Int): Int {
        val totalCodewords = rawDataModules(version) / 8
        return totalCodewords - EC_CODEWORDS_PER_BLOCK_L[version] * NUM_BLOCKS_L[version]
    }

    private fun addEccAndInterleave(data: ByteArray, version: Int): ByteArray {
        val numBlocks = NUM_BLOCKS_L[version]
        val blockEccLen = EC_CODEWORDS_PER_BLOCK_L[version]
        val rawCodewords = rawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks

        val rsDiv = reedSolomonComputeDivisor(blockEccLen)
        val blocks = ArrayList<ByteArray>(numBlocks)
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortBlockLen - blockEccLen + if (i < numShortBlocks) 0 else 1
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val ecc = reedSolomonComputeRemainder(dat, rsDiv)
            val block = ByteArray(datLen + blockEccLen)
            dat.copyInto(block, 0)
            ecc.copyInto(block, datLen)
            blocks.add(block)
        }

        val result = ByteArray(rawCodewords)
        var out = 0
        for (i in 0 until blocks.last().size) {
            for (j in 0 until numBlocks) {
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    val idx = if (j < numShortBlocks && i > shortBlockLen - blockEccLen) i - 1 else i
                    if (idx < blocks[j].size) {
                        result[out++] = blocks[j][idx]
                    }
                }
            }
        }
        return result
    }

    private fun reedSolomonComputeDivisor(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < degree) {
                    result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonComputeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
        val result = ByteArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() xor result[0].toInt()) and 0xFF
            result.copyInto(result, destinationOffset = 0, startIndex = 1, endIndex = result.size)
            result[result.lastIndex] = 0
            for (i in divisor.indices) {
                result[i] = (result[i].toInt() xor gfMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
            }
        }
        return result
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun setFunction(size: Int, x: Int, y: Int, isDark: Boolean, mods: BooleanArray, fn: BooleanArray) {
        val idx = y * size + x
        mods[idx] = isDark
        fn[idx] = true
    }

    private fun drawFunctionPatterns(version: Int, size: Int, mods: BooleanArray, fn: BooleanArray) {
        // Timing patterns
        for (i in 0 until size) {
            setFunction(size, 6, i, i % 2 == 0, mods, fn)
            setFunction(size, i, 6, i % 2 == 0, mods, fn)
        }
        // Finder patterns + separators
        drawFinder(size, 3, 3, mods, fn)
        drawFinder(size, size - 4, 3, mods, fn)
        drawFinder(size, 3, size - 4, mods, fn)

        // Alignment patterns
        val align = alignmentPositions(version, size)
        for (i in align.indices) {
            for (j in align.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == align.lastIndex) || (i == align.lastIndex && j == 0)) continue
                drawAlignment(size, align[i], align[j], mods, fn)
            }
        }

        // Reserve format bits (drawn after mask selection) and dark module
        drawFormatBits(size, 0, mods, fn)
        // Version information (version >= 7)
        if (version >= 7) {
            var rem = version
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val bit = ((bits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                setFunction(size, a, b, bit, mods, fn)
                setFunction(size, b, a, bit, mods, fn)
            }
        }
    }

    private fun drawFinder(size: Int, cx: Int, cy: Int, mods: BooleanArray, fn: BooleanArray) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val dist = maxOf(abs(dx), abs(dy))
                    setFunction(size, x, y, dist != 2 && dist != 4, mods, fn)
                }
            }
        }
    }

    private fun drawAlignment(size: Int, cx: Int, cy: Int, mods: BooleanArray, fn: BooleanArray) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(abs(dx), abs(dy))
                setFunction(size, cx + dx, cy + dy, dist != 1, mods, fn)
            }
        }
    }

    private fun alignmentPositions(version: Int, size: Int): IntArray {
        if (version == 1) return IntArray(0)
        val numAlign = version / 7 + 2
        val step = if (version == 32) 26 else ((version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2)) * 2
        val result = IntArray(numAlign)
        result[0] = 6
        var pos = size - 7
        for (i in numAlign - 1 downTo 1) {
            result[i] = pos
            pos -= step
        }
        return result
    }

    private fun drawFormatBits(size: Int, mask: Int, mods: BooleanArray, fn: BooleanArray) {
        // Level L format indicator = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) setFunction(size, 8, i, ((bits ushr i) and 1) != 0, mods, fn)
        setFunction(size, 8, 7, ((bits ushr 6) and 1) != 0, mods, fn)
        setFunction(size, 8, 8, ((bits ushr 7) and 1) != 0, mods, fn)
        setFunction(size, 7, 8, ((bits ushr 8) and 1) != 0, mods, fn)
        for (i in 9..14) setFunction(size, 14 - i, 8, ((bits ushr i) and 1) != 0, mods, fn)

        for (i in 0..7) setFunction(size, size - 1 - i, 8, ((bits ushr i) and 1) != 0, mods, fn)
        for (i in 8..14) setFunction(size, 8, size - 15 + i, ((bits ushr i) and 1) != 0, mods, fn)
        setFunction(size, 8, size - 8, true, mods, fn)
    }

    private fun drawCodewords(size: Int, codewords: ByteArray, mods: BooleanArray, fn: BooleanArray) {
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    val idx = y * size + x
                    if (!fn[idx] && bitIdx < totalBits) {
                        val b = codewords[bitIdx ushr 3].toInt() and 0xFF
                        mods[idx] = ((b ushr (7 - (bitIdx and 7))) and 1) != 0
                        bitIdx++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(size: Int, mask: Int, mods: BooleanArray, fn: BooleanArray) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                if (fn[idx]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> (x * y) % 2 + (x * y) % 3 == 0
                    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                    7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                    else -> false
                }
                if (invert) mods[idx] = !mods[idx]
            }
        }
    }

    private fun penaltyScore(size: Int, mods: BooleanArray): Int {
        var score = 0
        // Adjacent modules in rows/columns
        for (y in 0 until size) {
            var runColor = mods[y * size]
            var runLen = 1
            for (x in 1 until size) {
                val c = mods[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }
        for (x in 0 until size) {
            var runColor = mods[x]
            var runLen = 1
            for (y in 1 until size) {
                val c = mods[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }
        // 2x2 blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = mods[y * size + x]
                if (c == mods[y * size + x + 1] && c == mods[(y + 1) * size + x] && c == mods[(y + 1) * size + x + 1]) {
                    score += 3
                }
            }
        }
        // Dark module balance
        var dark = 0
        for (m in mods) if (m) dark++
        val total = size * size
        val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
        if (k > 0) score += k * 10
        return score
    }

    private class BitBuffer {
        private var data = ByteArray(64)
        var length: Int = 0
            private set

        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                val bit = (value ushr i) and 1
                val byteIdx = length ushr 3
                if (byteIdx >= data.size) {
                    data = data.copyOf(data.size * 2)
                }
                if (bit != 0) {
                    data[byteIdx] = (data[byteIdx].toInt() or (1 shl (7 - (length and 7)))).toByte()
                }
                length++
            }
        }

        fun toByteArray(): ByteArray = data.copyOf((length + 7) ushr 3)
    }
}

/**
 * Renders a crisp high-contrast QR matrix with standard quiet zone on a white tile
 * so phone cameras scan reliably under every dark or grey Ikna palette.
 */
@Composable
fun IknaQrCode(
    content: String,
    modifier: Modifier = Modifier,
    maxSize: Dp = 220.dp
) {
    val matrix = remember(content) {
        runCatching { QrCodeEncoder.encode(content) }.getOrNull()
    } ?: return
    val shape = iknaTileShape()
    Box(
        modifier = modifier
            .sizeIn(maxWidth = maxSize, maxHeight = maxSize)
            .aspectRatio(1f)
            .background(Color.White, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val quiet = 2
            val total = matrix.size + quiet * 2
            val cell = minOf(size.width, size.height) / total
            val offsetX = (size.width - cell * total) / 2f
            val offsetY = (size.height - cell * total) / 2f
            val ink = Color(0xFF101318)
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix[x, y]) {
                        drawRect(
                            color = ink,
                            topLeft = Offset(offsetX + (x + quiet) * cell, offsetY + (y + quiet) * cell),
                            size = Size(cell + 0.35f, cell + 0.35f)
                        )
                    }
                }
            }
        }
    }
}
