package dev.ikna.ui.stats

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
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

        val sansSemi = loadFont("/font/geologica_semibold.ttf", Font.BOLD, 42f)
        val sansReg = loadFont("/font/geologica_regular.ttf", Font.PLAIN, 20f)
        val monoMed = loadFont("/font/ibm_plex_mono_medium.ttf", Font.PLAIN, 16f)
        val monoBig = loadFont("/font/ibm_plex_mono_semibold.ttf", Font.BOLD, 38f)

        // Top eyebrow
        g.color = accent
        g.fill(RoundRectangle2D.Float(64f, 44f, 44f, 4f, 4f, 4f))
        g.font = monoMed.deriveFont(15f)
        g.drawString(data.headerText, 122f, 52f)

        g.color = muted
        val dateW = g.fontMetrics.stringWidth(data.generatedDate)
        g.drawString(data.generatedDate, w - 64f - dateW, 52f)

        // Avatar badge + Name + Bio/Subtitle
        drawDesktopAvatarBadge(g, 64f, 76f, 82f, data, panel, line, ink, accent, sansSemi)

        g.color = ink
        g.font = sansSemi.deriveFont(42f)
        g.drawString(fitText(g, data.profileName, 840), 166f, 118f)

        g.color = muted
        g.font = sansReg.deriveFont(20f)
        val subLine = if (data.profileBio.isNotBlank()) "${data.profileBio}  ·  ${data.subtitle}" else data.subtitle
        g.drawString(fitText(g, subLine, 960), 166f, 150f)

        // Divider
        g.color = withAlpha(line, 140)
        g.stroke = BasicStroke(1.2f)
        g.drawLine(64, 184, w - 64, 184)

        // LEFT COLUMN: 2x2 KPI Grid + Memory Maturity
        val leftX = 64f
        val leftW = 564f
        val gap = 18f
        val tileW = (leftW - gap) / 2f
        val tileH = 132f
        val gridY = 210f

        val paceStr = if (data.medianSecPerCard30d > 0) " · ${data.medianSecPerCard30d}s" else ""
        val stabStr = if (data.avgStabilityDays >= 1.0) " · ~${data.avgStabilityDays.roundToInt()}d" else ""
        val tiles = listOf(
            Triple(data.matureCards.toString(), data.labelMature.ifBlank { data.labelTargets }, "${data.targets} ${data.labelTargets}"),
            Triple(
                data.recallPercentText,
                data.labelRecall,
                if (data.repeatAnswers >= 5) "${data.recalledRepeatAnswers}/${data.repeatAnswers}" else "${data.answers} ${data.labelAnswers}"
            ),
            Triple(data.studyTimeText, data.labelStudyTime.ifBlank { data.labelAnswers }, "${data.answers} ${data.labelAnswers}$paceStr"),
            Triple("${data.activeDays30d}/30", data.labelActiveDays.ifBlank { data.periodLabel }, "FSRS$stabStr")
        )

        tiles.forEachIndexed { idx, (value, label, detail) ->
            val col = idx % 2
            val row = idx / 2
            val x = leftX + col * (tileW + gap)
            val y = gridY + row * (tileH + gap)
            val rr = RoundRectangle2D.Float(x, y, tileW, tileH, 14f, 14f)

            g.color = panel
            g.fill(rr)
            g.color = withAlpha(line, 155)
            g.stroke = BasicStroke(1.2f)
            g.draw(rr)

            g.color = if (idx < 2) accent else withAlpha(muted, 175)
            g.fill(RoundRectangle2D.Float(x + 20f, y + 22f, 4f, 40f, 3f, 3f))

            g.color = if (idx < 2) accent else ink
            g.font = monoBig.deriveFont(38f)
            g.drawString(value, x + 36f, y + 58f)

            g.color = ink
            g.font = sansSemi.deriveFont(15f)
            g.drawString(fitText(g, label, (tileW - 40f).toInt()), x + 20f, y + 90f)

            g.color = muted
            g.font = sansReg.deriveFont(14f)
            g.drawString(fitText(g, detail, (tileW - 40f).toInt()), x + 20f, y + 114f)
        }

        // Memory Maturity Panel
        val matRR = RoundRectangle2D.Float(leftX, 506f, leftW, 120f, 14f, 14f)
        g.color = panel
        g.fill(matRR)
        g.color = withAlpha(line, 155)
        g.draw(matRR)

        val studied = (data.matureCards + data.youngCards + data.learningCards).coerceAtLeast(0)
        g.font = monoMed.deriveFont(14f)
        g.color = muted
        g.drawString(data.labelMaturity.ifBlank { "MEMORY MATURITY" }.uppercase(), leftX + 22f, 536f)
        val totStr = studied.toString()
        g.drawString(totStr, leftX + leftW - 22f - g.fontMetrics.stringWidth(totStr), 536f)

        val barX = leftX + 22f
        val barY = 554f
        val barW = leftW - 44f
        val barH = 18f
        g.color = withAlpha(line, 90)
        g.fill(RoundRectangle2D.Float(barX, barY, barW, barH, 6f, 6f))
        if (studied > 0) {
            val mW = barW * (data.matureCards.toFloat() / studied)
            val yW = barW * (data.youngCards.toFloat() / studied)
            val lW = (barW - mW - yW).coerceAtLeast(0f)
            if (mW > 0f) { g.color = accent; g.fillRect(barX.toInt(), barY.toInt(), mW.toInt(), barH.toInt()) }
            if (yW > 0f) { g.color = withAlpha(accent, 130); g.fillRect((barX + mW).toInt(), barY.toInt(), yW.toInt(), barH.toInt()) }
            if (lW > 0f) { g.color = withAlpha(muted, 110); g.fillRect((barX + mW + yW).toInt(), barY.toInt(), lW.toInt(), barH.toInt()) }
        }
        g.font = sansReg.deriveFont(14f)
        g.color = accent
        g.drawString("${data.labelMature.ifBlank { "Mature" }}: ${data.matureCards}", barX, 604f)
        g.color = muted
        val midLeg = "${data.labelYoung.ifBlank { "Young" }}: ${data.youngCards}"
        g.drawString(midLeg, leftX + (leftW - g.fontMetrics.stringWidth(midLeg)) / 2f, 604f)
        val rightLeg = "${data.labelLearning.ifBlank { "Learning" }}: ${data.learningCards}"
        g.drawString(rightLeg, leftX + leftW - 22f - g.fontMetrics.stringWidth(rightLeg), 604f)

        // RIGHT COLUMN: 12-Week Heatmap + Top 3 Decks
        val rightX = 654f
        val rightW = 482f

        val heatRR = RoundRectangle2D.Float(rightX, 210f, rightW, 218f, 14f, 14f)
        g.color = panel
        g.fill(heatRR)
        g.color = withAlpha(line, 155)
        g.draw(heatRR)

        g.font = monoMed.deriveFont(14f)
        g.color = muted
        g.drawString(data.labelHeatmap.ifBlank { "12-WEEK ACTIVITY" }.uppercase(), rightX + 22f, 240f)

        val days = if (data.dailyActivity84d.size == 84) data.dailyActivity84d else List(84) { 0 }
        val maxDay = (days.maxOrNull() ?: 0).coerceAtLeast(1)
        val cols = 12
        val rows = 7
        val hGap = 6f
        val hmX = rightX + 22f
        val hmY = 258f
        val hmW = rightW - 44f
        val hmH = 148f
        val cW = (hmW - hGap * (cols - 1)) / cols
        val cH = (hmH - hGap * (rows - 1)) / rows
        for (i in 0 until 84) {
            val c = i / 7
            val r = i % 7
            val cnt = days[i]
            g.color = when {
                cnt <= 0 -> withAlpha(line, 80)
                cnt <= maxDay / 4 -> withAlpha(accent, 90)
                cnt <= maxDay / 2 -> withAlpha(accent, 145)
                cnt <= (maxDay * 3) / 4 -> withAlpha(accent, 200)
                else -> accent
            }
            val cx = hmX + c * (cW + hGap)
            val cy = hmY + r * (cH + hGap)
            g.fill(RoundRectangle2D.Float(cx, cy, cW, cH, 4f, 4f))
        }

        val deckRR = RoundRectangle2D.Float(rightX, 446f, rightW, 180f, 14f, 14f)
        g.color = panel
        g.fill(deckRR)
        g.color = withAlpha(line, 155)
        g.draw(deckRR)

        g.font = monoMed.deriveFont(14f)
        g.color = muted
        g.drawString(data.labelDecks.ifBlank { "ACTIVE DECKS" }.uppercase(), rightX + 22f, 476f)

        val decks = data.topDecks.take(3)
        decks.forEachIndexed { idx, deck ->
            val dy = 508f + idx * 40f
            g.font = sansReg.deriveFont(15f)
            val countText = "${deck.known}/${deck.total}"
            val cw = g.fontMetrics.stringWidth(countText)
            g.color = ink
            g.drawString(fitText(g, deck.title, (rightW - 60f - cw).toInt()), rightX + 22f, dy)
            g.color = muted
            g.drawString(countText, rightX + rightW - 22f - cw, dy)

            val frac = if (deck.total > 0) (deck.known.toFloat() / deck.total.toFloat()).coerceIn(0f, 1f) else 0f
            val dBarY = dy + 8f
            val dBarW = rightW - 44f
            g.color = withAlpha(line, 90)
            g.fill(RoundRectangle2D.Float(rightX + 22f, dBarY, dBarW, 6f, 3f, 3f))
            if (frac > 0f) {
                g.color = accent
                g.fill(RoundRectangle2D.Float(rightX + 22f, dBarY, dBarW * frac, 6f, 3f, 3f))
            }
        }

        // Footer
        g.color = withAlpha(line, 120)
        g.drawLine(64, h - 62, w - 64, h - 62)

        g.color = muted
        g.font = monoMed.deriveFont(15f)
        g.drawString(data.footerText, 64f, (h - 28).toFloat())

        val rw = g.fontMetrics.stringWidth(data.recoveredText)
        g.color = accent
        g.drawString(data.recoveredText, w - 64f - rw, (h - 28).toFloat())
    } finally {
        g.dispose()
    }
    val out = ByteArrayOutputStream()
    ImageIO.write(img, "png", out)
    return out.toByteArray()
}

private fun drawDesktopAvatarBadge(
    g: Graphics2D,
    x: Float,
    y: Float,
    size: Float,
    data: ProfileStatsCardData,
    panel: Color,
    line: Color,
    ink: Color,
    accent: Color,
    sansSemi: Font
) {
    val rr = RoundRectangle2D.Float(x, y, size, size, 14f, 14f)
    g.color = panel
    g.fill(rr)

    val customImg = data.profileAvatar.takeIf { it.isNotBlank() }?.let { path ->
        runCatching {
            val f = File(path)
            if (f.isFile) ImageIO.read(f) else null
        }.getOrNull()
    }
    if (customImg != null) {
        val oldClip = g.clip
        g.clip = rr
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
        g.color = withAlpha(line, 180)
        g.stroke = BasicStroke(1.6f)
        g.draw(rr)
        return
    }

    g.color = withAlpha(line, 180)
    g.stroke = BasicStroke(1.6f)
    g.draw(rr)

    val pad = size * 0.20f
    val cell = (size - pad * 2f) / 7f
    val ox = x + (size - cell * 6f) / 2f
    val oy = y + (size - cell * 7f) / 2f
    for (r in PROFILE_CARD_EMBLEM.indices) {
        val row = PROFILE_CARD_EMBLEM[r]
        for (c in row.indices) {
            val ch = row[c]
            if (ch == '.') continue
            g.color = if (ch == 'A') accent else ink
            g.fill(RoundRectangle2D.Float(ox + c * cell, oy + r * cell, cell - 1.5f, cell - 1.5f, 2f, 2f))
        }
    }
}

private fun drawBackdropEmblem(g: Graphics2D, w: Int, h: Int, bg: Color, ink: Color, accent: Color) {
    val luma = (0.2126 * bg.red + 0.7152 * bg.green + 0.0722 * bg.blue) / 255.0
    val inkMix = if (luma < 0.01) 0.14f else 0.075f
    val accMix = if (luma < 0.01) 0.24f else 0.16f
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
