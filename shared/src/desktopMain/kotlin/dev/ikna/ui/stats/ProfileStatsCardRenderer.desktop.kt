package dev.ikna.ui.stats

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun renderProfileStatsCardPng(data: ProfileStatsCardData): ByteArray {
    val w = 1200
    val h = 675
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

        // Base background
        g.color = bg
        g.fillRect(0, 0, w, h)

        // Subtle tilted Ikna pixel-emblem backdrop
        drawBackdropEmblem(g, w, h, bg, ink, accent)

        val sansSemi = loadFont("/font/geologica_semibold.ttf", Font.BOLD, 48f)
        val sansReg = loadFont("/font/geologica_regular.ttf", Font.PLAIN, 22f)
        val monoMed = loadFont("/font/ibm_plex_mono_medium.ttf", Font.PLAIN, 17f)
        val monoBig = loadFont("/font/ibm_plex_mono_semibold.ttf", Font.BOLD, 46f)

        // Top eyebrow + accent rule
        g.color = accent
        g.fill(RoundRectangle2D.Float(68f, 56f, 44f, 4f, 4f, 4f))
        g.font = monoMed.deriveFont(16f)
        g.drawString(data.headerText, 126f, 64f)

        g.color = muted
        val dateText = data.generatedDate
        val dateWidth = g.fontMetrics.stringWidth(dateText)
        g.drawString(dateText, w - 68f - dateWidth, 64f)

        // Profile name + subtitle
        g.color = ink
        g.font = sansSemi.deriveFont(48f)
        g.drawString(data.profileName.take(28), 68f, 134f)

        g.color = muted
        g.font = sansReg.deriveFont(22f)
        g.drawString(data.subtitle, 68f, 170f)

        // Hairline divider
        g.color = Color(line.red, line.green, line.blue, 140)
        g.stroke = BasicStroke(1.2f)
        g.drawLine(68, 200, w - 68, 200)

        // 4 metric tiles in a 2x2 editorial grid
        val tiles = listOf(
            Triple(data.targets.toString(), data.labelTargets, true),
            Triple(data.firstRecorded.toString(), data.labelNew, false),
            Triple(data.answers.toString(), data.labelAnswers, false),
            Triple(
                data.recallPercentText,
                if (data.repeatAnswers >= 5) "${data.labelRecall} (${data.recalledRepeatAnswers}/${data.repeatAnswers})" else data.labelRecall,
                true
            )
        )
        val gap = 24
        val tileW = (w - 136 - gap) / 2
        val tileH = 162
        val startY = 232
        tiles.forEachIndexed { idx, (value, label, highlight) ->
            val col = idx % 2
            val row = idx / 2
            val x = 68 + col * (tileW + gap)
            val y = startY + row * (tileH + gap)

            val rr = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), tileW.toFloat(), tileH.toFloat(), 18f, 18f)
            g.color = panel
            g.fill(rr)
            g.color = Color(line.red, line.green, line.blue, 155)
            g.stroke = BasicStroke(1.2f)
            g.draw(rr)

            // Left semantic accent tick
            g.color = if (highlight) accent else Color(muted.red, muted.green, muted.blue, 170)
            g.fill(RoundRectangle2D.Float((x + 26).toFloat(), (y + 30).toFloat(), 4f, 44f, 4f, 4f))

            g.color = ink
            g.font = monoBig.deriveFont(46f)
            g.drawString(value, (x + 46).toFloat(), (y + 72).toFloat())

            g.color = muted
            g.font = sansReg.deriveFont(19f)
            g.drawString(fitText(g, label, tileW - 56), (x + 28).toFloat(), (y + 124).toFloat())
        }

        // Footer bar
        g.color = Color(line.red, line.green, line.blue, 120)
        g.drawLine(68, h - 72, w - 68, h - 72)

        g.color = muted
        g.font = monoMed.deriveFont(15f)
        g.drawString(data.footerText, 68f, (h - 38).toFloat())

        val recoveredNote = data.recoveredText
        val rw = g.fontMetrics.stringWidth(recoveredNote)
        g.color = accent
        g.drawString(recoveredNote, w - 68f - rw, (h - 38).toFloat())
    } finally {
        g.dispose()
    }
    val out = ByteArrayOutputStream()
    ImageIO.write(img, "png", out)
    return out.toByteArray()
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

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    (a.red + (b.red - a.red) * t).toInt().coerceIn(0, 255),
    (a.green + (b.green - a.green) * t).toInt().coerceIn(0, 255),
    (a.blue + (b.blue - a.blue) * t).toInt().coerceIn(0, 255)
)

private fun fitText(g: Graphics2D, text: String, maxWidth: Int): String {
    val fm = g.fontMetrics
    if (fm.stringWidth(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && fm.stringWidth(text.substring(0, end) + "…") > maxWidth) {
        end--
    }
    return text.substring(0, end) + "…"
}

private fun loadFont(resourcePath: String, fallbackStyle: Int, size: Float): Font {
    val stream = ProfileStatsCardData::class.java.getResourceAsStream(resourcePath)
    if (stream != null) {
        runCatching {
            stream.use { return Font.createFont(Font.TRUETYPE_FONT, it).deriveFont(size) }
        }
    }
    val family = if (resourcePath.contains("mono")) Font.MONOSPACED else Font.SANS_SERIF
    return Font(family, fallbackStyle, size.toInt()).deriveFont(size)
}
