package dev.ikna.ui.stats

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

actual fun loadProfileAvatarBitmap(path: String): ImageBitmap? = runCatching {
    val file = File(path)
    if (!file.isFile) return null
    ImageIO.read(file)?.toComposeImageBitmap()
}.getOrNull()

actual fun renderProfileStatsCardPng(data: ProfileStatsCardData): ByteArray {
    val w = 1200
    val h = 720
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        val bg = Color(data.bgArgb, true)
        val panel = Color(data.panelArgb, true)
        val line = Color(data.lineArgb, true)
        val ink = Color(data.inkArgb, true)
        val muted = Color(data.mutedArgb, true)
        val accent = Color(data.accentArgb, true)

        g.color = bg
        g.fillRect(0, 0, w, h)
        drawBackdropEmblem(g, w, h, bg, ink, accent)

        val sansSemi = loadFont("/font/geologica_semibold.ttf", Font.BOLD, 38f)
        val sansReg = loadFont("/font/geologica_regular.ttf", Font.PLAIN, 18f)
        val monoMed = loadFont("/font/ibm_plex_mono_medium.ttf", Font.PLAIN, 14f)
        val monoBig = loadFont("/font/ibm_plex_mono_semibold.ttf", Font.BOLD, 40f)

        val marginX = 68f
        val rightEdge = w - marginX

        // 1. Open Editorial Header: circular avatar + identity + single date stamp top-right
        drawDesktopAvatarCircle(g, marginX, 54f, 76f, data, panel, line, ink, accent)

        g.color = ink
        g.font = sansSemi.deriveFont(36f)
        g.drawString(fitText(g, data.profileName, 700), 166f, 92f)

        val subLine = data.profileBio.ifBlank { data.periodLabel }
        if (subLine.isNotBlank()) {
            g.color = muted
            g.font = sansReg.deriveFont(18f)
            g.drawString(fitText(g, subLine, 720), 166f, 122f)
        }

        // Quiet single date stamp top-right (no duplicate footer date, no ALL-CAPS eyebrow)
        g.font = monoMed.deriveFont(14f)
        val metaText = if (data.generatedDate.isNotBlank()) "ikna · ${data.generatedDate}" else "ikna"
        g.color = muted
        val metaW = g.fontMetrics.stringWidth(metaText)
        g.drawString(metaText, rightEdge - metaW, 88f)

        // Hairline 1
        g.color = withAlpha(line, 125)
        g.stroke = BasicStroke(1f)
        g.drawLine(marginX.toInt(), 160, rightEdge.toInt(), 160)

        // 2. Open Typographic KPI Strip (4 aligned columns, zero boxes, zero accent stripes)
        val colW = 248f
        val colGap = 24f
        val stabDetail = if (data.avgStabilityDays >= 1.0) "~${data.avgStabilityDays.roundToInt()} ${data.unitDay}" else ""
        val recallDetail = if (data.repeatAnswers >= 5) "${data.recalledRepeatAnswers}/${data.repeatAnswers}" else ""
        val timeDetail = buildString {
            if (data.answers > 0) {
                append(data.answers)
                if (data.medianSecPerCard30d > 0) append(" · ${data.medianSecPerCard30d}s")
            }
        }
        val kpis = listOf(
            KpiEntry(
                value = data.matureCards.toString(),
                label = data.labelMature.ifBlank { data.labelTargets },
                detail = stabDetail,
                emphasized = data.matureCards > 0
            ),
            KpiEntry(
                value = data.recallPercentText,
                label = data.labelRecall,
                detail = recallDetail,
                emphasized = data.repeatAnswers >= 5
            ),
            KpiEntry(
                value = data.studyTimeText,
                label = data.labelStudyTime.ifBlank { data.labelAnswers },
                detail = timeDetail,
                emphasized = false
            ),
            KpiEntry(
                value = "${data.activeDays30d}/30",
                label = data.labelActiveDays.ifBlank { data.periodLabel },
                detail = "",
                emphasized = false
            )
        )

        kpis.forEachIndexed { idx, item ->
            val cx = marginX + idx * (colW + colGap)
            g.color = if (item.emphasized) accent else ink
            g.font = monoBig.deriveFont(40f)
            g.drawString(item.value, cx, 232f)

            g.color = ink
            g.font = sansSemi.deriveFont(15f)
            g.drawString(fitText(g, item.label, colW.toInt()), cx, 262f)

            if (item.detail.isNotBlank()) {
                g.color = muted
                g.font = monoMed.deriveFont(13f)
                g.drawString(fitText(g, item.detail, colW.toInt()), cx, 286f)
            }
        }

        // Hairline 2
        g.color = withAlpha(line, 125)
        g.stroke = BasicStroke(1f)
        g.drawLine(marginX.toInt(), 316, rightEdge.toInt(), 316)

        // 3. Balanced Two-Column Bottom Area
        // LEFT: 12-week activity heatmap (crisp 1:1 squares, header bound to exact grid width)
        val days = if (data.dailyActivity84d.size == 84) data.dailyActivity84d else List(84) { 0 }
        val activeDays84d = days.count { it > 0 }
        val maxDay = (days.maxOrNull() ?: 0).coerceAtLeast(1)
        val cols = 12
        val rows = 7
        val cellSize = 29f
        val cellGap = 7f
        val gridW = cols * cellSize + (cols - 1) * cellGap // 425px
        val hmX = marginX
        val hmY = 390f

        g.font = sansSemi.deriveFont(16f)
        g.color = ink
        val heatLabel = data.labelHeatmap.ifBlank { "12-week activity" }
        val activeCountStr = "$activeDays84d / 84 ${data.unitDay}"
        g.font = monoMed.deriveFont(13f)
        val activeCountW = g.fontMetrics.stringWidth(activeCountStr)
        g.color = muted
        g.drawString(activeCountStr, hmX + gridW - activeCountW, 366f)

        g.font = sansSemi.deriveFont(16f)
        g.color = ink
        g.drawString(fitText(g, heatLabel, (gridW - activeCountW - 14f).toInt()), hmX, 366f)

        for (i in 0 until 84) {
            val c = i / 7
            val r = i % 7
            val cnt = days[i]
            g.color = when {
                cnt <= 0 -> withAlpha(line, 92)
                cnt <= maxDay / 4 -> withAlpha(accent, 95)
                cnt <= maxDay / 2 -> withAlpha(accent, 150)
                cnt <= (maxDay * 3) / 4 -> withAlpha(accent, 205)
                else -> accent
            }
            val cx = hmX + c * (cellSize + cellGap)
            val cy = hmY + r * (cellSize + cellGap)
            g.fill(RoundRectangle2D.Float(cx, cy, cellSize, cellSize, 4f, 4f))
        }

        // RIGHT: Memory Maturity + Calm Active Decks (zero 0/3000 denominators)
        val rightX = 562f
        val rightW = rightEdge - rightX // 570px

        val studied = (data.matureCards + data.youngCards + data.learningCards).coerceAtLeast(0)
        g.font = sansSemi.deriveFont(16f)
        g.color = ink
        g.drawString(data.labelMaturity.ifBlank { "Memory maturity" }, rightX, 366f)

        g.font = monoMed.deriveFont(14f)
        g.color = muted
        val totStr = studied.toString()
        g.drawString(totStr, rightEdge - g.fontMetrics.stringWidth(totStr), 366f)

        val barY = 390f
        val barH = 8f
        val barClip = RoundRectangle2D.Float(rightX, barY, rightW, barH, 4f, 4f)
        val savedClip = g.clip
        g.clip = barClip
        g.color = withAlpha(line, 92)
        g.fill(barClip)
        if (studied > 0) {
            val mW = rightW * (data.matureCards.toFloat() / studied)
            val yW = rightW * (data.youngCards.toFloat() / studied)
            val lW = (rightW - mW - yW).coerceAtLeast(0f)
            if (mW > 0f) {
                g.color = accent
                g.fillRect(rightX.toInt(), barY.toInt(), mW.roundToInt().coerceAtLeast(1), barH.toInt())
            }
            if (yW > 0f) {
                g.color = withAlpha(accent, 140)
                g.fillRect((rightX + mW).roundToInt(), barY.toInt(), yW.roundToInt().coerceAtLeast(1), barH.toInt())
            }
            if (lW > 0f) {
                g.color = withAlpha(muted, 115)
                g.fillRect((rightX + mW + yW).roundToInt(), barY.toInt(), lW.roundToInt().coerceAtLeast(1), barH.toInt())
            }
        }
        g.clip = savedClip

        // Single-line measured dot legend (never wraps or collides)
        val legendItems = listOf(
            Triple(accent, data.labelMature.ifBlank { "Mature" }, data.matureCards),
            Triple(withAlpha(accent, 140), data.labelYoung.ifBlank { "Young" }, data.youngCards),
            Triple(withAlpha(muted, 115), data.labelLearning.ifBlank { "Learning" }, data.learningCards)
        )
        g.font = sansReg.deriveFont(14f)
        var legX = rightX
        val legBaseline = 424f
        legendItems.forEach { (dotColor, label, count) ->
            val txt = "$label $count"
            val itemW = 7f + 8f + g.fontMetrics.stringWidth(txt)
            if (legX + itemW <= rightEdge + 4f) {
                g.color = dotColor
                g.fill(Ellipse2D.Float(legX, legBaseline - 9f, 7f, 7f))
                g.color = muted
                g.drawString(txt, legX + 15f, legBaseline)
                legX += itemW + 24f
            }
        }

        // Subtle divider between Maturity and Decks
        g.color = withAlpha(line, 100)
        g.stroke = BasicStroke(1f)
        g.drawLine(rightX.toInt(), 454, rightEdge.toInt(), 454)

        // Active Decks (calm positive progress only)
        g.font = sansSemi.deriveFont(16f)
        g.color = ink
        g.drawString(data.labelDecks.ifBlank { "Decks" }, rightX, 492f)

        val decks = data.topDecks.take(3)
        decks.forEachIndexed { idx, deck ->
            val dy = 532f + idx * 44f
            val statusText = formatDeckProgressQuiet(
                deck = deck,
                matureFmt = data.labelDeckMatureFmt,
                studiedFmt = data.labelDeckStudiedFmt,
                readyLabel = data.labelDeckReady
            )
            g.font = monoMed.deriveFont(13f)
            val statusW = if (statusText.isNotBlank()) g.fontMetrics.stringWidth(statusText) else 0
            if (statusText.isNotBlank()) {
                g.color = if (deck.known > 0) accent else muted
                g.drawString(statusText, rightEdge - statusW, dy)
            }

            g.font = sansReg.deriveFont(15f)
            g.color = ink
            val titleMaxW = (rightW - statusW - 20f).toInt().coerceAtLeast(120)
            g.drawString(fitText(g, deck.title, titleMaxW), rightX, dy)

            if (idx < decks.lastIndex) {
                g.color = withAlpha(line, 65)
                g.stroke = BasicStroke(1f)
                g.drawLine(rightX.toInt(), (dy + 16f).toInt(), rightEdge.toInt(), (dy + 16f).toInt())
            }
        }

        // 4. Minimal Footer (hairline + quiet wordmark; recovered only when > 0)
        g.color = withAlpha(line, 95)
        g.stroke = BasicStroke(1f)
        g.drawLine(marginX.toInt(), h - 54, rightEdge.toInt(), h - 54)

        g.color = withAlpha(muted, 195)
        g.font = monoMed.deriveFont(14f)
        g.drawString("ikna", marginX, (h - 24).toFloat())

        if (data.recoveredCount > 0 && data.recoveredText.isNotBlank()) {
            val rw = g.fontMetrics.stringWidth(data.recoveredText)
            g.color = accent
            g.drawString(data.recoveredText, rightEdge - rw, (h - 24).toFloat())
        }
    } finally {
        g.dispose()
    }
    val out = ByteArrayOutputStream()
    ImageIO.write(img, "png", out)
    return out.toByteArray()
}

private data class KpiEntry(
    val value: String,
    val label: String,
    val detail: String,
    val emphasized: Boolean
)

private fun drawDesktopAvatarCircle(
    g: Graphics2D,
    x: Float,
    y: Float,
    size: Float,
    data: ProfileStatsCardData,
    panel: Color,
    line: Color,
    ink: Color,
    accent: Color
) {
    val circle = Ellipse2D.Float(x, y, size, size)
    g.color = panel
    g.fill(circle)

    val customImg = data.profileAvatar.takeIf { it.isNotBlank() }?.let { path ->
        runCatching {
            val f = File(path)
            if (f.isFile) ImageIO.read(f) else null
        }.getOrNull()
    }
    if (customImg != null) {
        val oldClip = g.clip
        g.clip = circle
        val side = minOf(customImg.width, customImg.height)
        val sx = (customImg.width - side) / 2
        val sy = (customImg.height - side) / 2
        g.drawImage(
            customImg,
            x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt(),
            sx, sy, sx + side, sy + side,
            null
        )
        g.clip = oldClip
        g.color = withAlpha(line, 165)
        g.stroke = BasicStroke(1.5f)
        g.draw(circle)
        return
    }

    g.color = withAlpha(line, 165)
    g.stroke = BasicStroke(1.5f)
    g.draw(circle)

    val pad = size * 0.24f
    val cell = (size - pad * 2f) / 7f
    val ox = x + (size - cell * 6f) / 2f
    val oy = y + (size - cell * 7f) / 2f
    for (r in PROFILE_CARD_EMBLEM.indices) {
        val row = PROFILE_CARD_EMBLEM[r]
        for (c in row.indices) {
            val ch = row[c]
            if (ch == '.') continue
            g.color = if (ch == 'A') accent else ink
            g.fill(RoundRectangle2D.Float(ox + c * cell, oy + r * cell, cell - 1.2f, cell - 1.2f, 2f, 2f))
        }
    }
}

private fun drawBackdropEmblem(g: Graphics2D, w: Int, h: Int, bg: Color, ink: Color, accent: Color) {
    val luma = (0.2126 * bg.red + 0.7152 * bg.green + 0.0722 * bg.blue) / 255.0
    val inkMix = if (luma < 0.01) 0.11f else 0.06f
    val accMix = if (luma < 0.01) 0.18f else 0.12f
    val inkCell = blend(bg, ink, inkMix)
    val accCell = blend(bg, accent, accMix)

    val saved = g.transform
    g.rotate(Math.toRadians(-14.0), w / 2.0, h / 2.0)
    val cell = 9
    val pitchX = 160
    val pitchY = 148
    for (row in -2..7) {
        val stagger = if (row % 2 == 0) 0 else pitchX / 2
        val baseY = row * pitchY - 80
        for (col in -2..10) {
            val baseX = col * pitchX + stagger - 80
            for (r in PROFILE_CARD_EMBLEM.indices) {
                val pattern = PROFILE_CARD_EMBLEM[r]
                for (c in pattern.indices) {
                    val ch = pattern[c]
                    if (ch == '.') continue
                    g.color = if (ch == 'A') accCell else inkCell
                    g.fillRect(baseX + c * cell, baseY + r * cell, cell, cell)
                }
            }
        }
    }
    g.transform = saved
}

private fun withAlpha(c: Color, alpha: Int): Color = Color(c.red, c.green, c.blue, alpha.coerceIn(0, 255))

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    (a.red + (b.red - a.red) * t).toInt().coerceIn(0, 255),
    (a.green + (b.green - a.green) * t).toInt().coerceIn(0, 255),
    (a.blue + (b.blue - a.blue) * t).toInt().coerceIn(0, 255)
)

private fun fitText(g: Graphics2D, text: String, maxWidth: Int): String {
    if (g.fontMetrics.stringWidth(text) <= maxWidth) return text
    var s = text
    while (s.length > 3 && g.fontMetrics.stringWidth("$s…") > maxWidth) {
        s = s.dropLast(1)
    }
    return "$s…"
}

private fun loadFont(resourcePath: String, style: Int, size: Float): Font =
    runCatching {
        val stream = ProfileStatsCardData::class.java.getResourceAsStream(resourcePath)
        if (stream != null) stream.use { Font.createFont(Font.TRUETYPE_FONT, it).deriveFont(size) }
        else Font(Font.SANS_SERIF, style, size.toInt())
    }.getOrElse { Font(Font.SANS_SERIF, style, size.toInt()) }
