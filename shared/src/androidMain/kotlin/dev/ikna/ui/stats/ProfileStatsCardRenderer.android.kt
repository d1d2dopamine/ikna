package dev.ikna.ui.stats

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

actual fun loadProfileAvatarBitmap(path: String): ImageBitmap? = runCatching {
    val file = File(path)
    if (!file.isFile) return null
    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
}.getOrNull()

actual fun renderProfileStatsCardPng(data: ProfileStatsCardData): ByteArray {
    val w = 1200
    val h = 720
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bg = data.bgArgb
    val panel = data.panelArgb
    val line = data.lineArgb
    val ink = data.inkArgb
    val muted = data.mutedArgb
    val accent = data.accentArgb

    canvas.drawColor(bg)
    drawAndroidBackdropEmblem(canvas, w, h, bg, ink, accent)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Top eyebrow
    fillPaint.color = accent
    canvas.drawRoundRect(RectF(64f, 44f, 108f, 48f), 4f, 4f, fillPaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 16f
    textPaint.color = accent
    canvas.drawText(data.headerText, 122f, 52f, textPaint)

    textPaint.color = muted
    val dateW = textPaint.measureText(data.generatedDate)
    canvas.drawText(data.generatedDate, w - 64f - dateW, 52f, textPaint)

    // Avatar badge (82x82) + Name + Bio/Subtitle
    drawAndroidAvatarBadge(canvas, 64f, 76f, 82f, data, bg, panel, line, ink, muted, accent)

    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 42f
    textPaint.color = ink
    canvas.drawText(ellipsize(textPaint, data.profileName, 840f), 166f, 118f, textPaint)

    textPaint.typeface = Typeface.SANS_SERIF
    textPaint.textSize = 20f
    textPaint.color = muted
    val subLine = if (data.profileBio.isNotBlank()) "${data.profileBio}  ·  ${data.subtitle}" else data.subtitle
    canvas.drawText(ellipsize(textPaint, subLine, 960f), 166f, 150f, textPaint)

    // Header divider
    strokePaint.color = withAlpha(line, 140)
    canvas.drawLine(64f, 184f, (w - 64).toFloat(), 184f, strokePaint)

    // LEFT COLUMN (x: 64..628): 2x2 Rich KPI Grid + Memory Maturity Bar
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
        val rect = RectF(x, y, x + tileW, y + tileH)

        fillPaint.color = panel
        canvas.drawRoundRect(rect, 14f, 14f, fillPaint)
        strokePaint.color = withAlpha(line, 155)
        canvas.drawRoundRect(rect, 14f, 14f, strokePaint)

        fillPaint.color = if (idx < 2) accent else withAlpha(muted, 175)
        canvas.drawRoundRect(RectF(x + 20f, y + 22f, x + 24f, y + 62f), 3f, 3f, fillPaint)

        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 38f
        textPaint.color = if (idx < 2) accent else ink
        canvas.drawText(value, x + 36f, y + 58f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 15f
        textPaint.color = ink
        canvas.drawText(ellipsize(textPaint, label, tileW - 40f), x + 20f, y + 90f, textPaint)

        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.textSize = 14f
        textPaint.color = muted
        canvas.drawText(ellipsize(textPaint, detail, tileW - 40f), x + 20f, y + 114f, textPaint)
    }

    // Memory Maturity Panel (left bottom, y: 506..626)
    val matRect = RectF(leftX, 506f, leftX + leftW, 626f)
    fillPaint.color = panel
    canvas.drawRoundRect(matRect, 14f, 14f, fillPaint)
    strokePaint.color = withAlpha(line, 155)
    canvas.drawRoundRect(matRect, 14f, 14f, strokePaint)

    val studied = (data.matureCards + data.youngCards + data.learningCards).coerceAtLeast(0)
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 14f
    textPaint.color = muted
    canvas.drawText(data.labelMaturity.ifBlank { "MEMORY MATURITY" }.uppercase(), leftX + 22f, 536f, textPaint)
    val totStr = studied.toString()
    canvas.drawText(totStr, leftX + leftW - 22f - textPaint.measureText(totStr), 536f, textPaint)

    val barX = leftX + 22f
    val barY = 554f
    val barW = leftW - 44f
    val barH = 18f
    fillPaint.color = withAlpha(line, 90)
    canvas.drawRoundRect(RectF(barX, barY, barX + barW, barY + barH), 6f, 6f, fillPaint)
    if (studied > 0) {
        val mW = barW * (data.matureCards.toFloat() / studied)
        val yW = barW * (data.youngCards.toFloat() / studied)
        val lW = (barW - mW - yW).coerceAtLeast(0f)
        if (mW > 0f) {
            fillPaint.color = accent
            canvas.drawRect(barX, barY, barX + mW, barY + barH, fillPaint)
        }
        if (yW > 0f) {
            fillPaint.color = withAlpha(accent, 130)
            canvas.drawRect(barX + mW, barY, barX + mW + yW, barY + barH, fillPaint)
        }
        if (lW > 0f) {
            fillPaint.color = withAlpha(muted, 110)
            canvas.drawRect(barX + mW + yW, barY, barX + barW, barY + barH, fillPaint)
        }
    }
    textPaint.typeface = Typeface.SANS_SERIF
    textPaint.textSize = 14f
    textPaint.color = accent
    canvas.drawText("${data.labelMature.ifBlank { "Mature" }}: ${data.matureCards}", barX, 604f, textPaint)
    textPaint.color = muted
    val midLeg = "${data.labelYoung.ifBlank { "Young" }}: ${data.youngCards}"
    canvas.drawText(midLeg, leftX + (leftW - textPaint.measureText(midLeg)) / 2f, 604f, textPaint)
    val rightLeg = "${data.labelLearning.ifBlank { "Learning" }}: ${data.learningCards}"
    canvas.drawText(rightLeg, leftX + leftW - 22f - textPaint.measureText(rightLeg), 604f, textPaint)

    // RIGHT COLUMN (x: 654..1136, width 482): 12-Week Activity Heatmap + Top Decks
    val rightX = 654f
    val rightW = 482f

    // Heatmap panel (y: 210..428)
    val heatRect = RectF(rightX, 210f, rightX + rightW, 428f)
    fillPaint.color = panel
    canvas.drawRoundRect(heatRect, 14f, 14f, fillPaint)
    strokePaint.color = withAlpha(line, 155)
    canvas.drawRoundRect(heatRect, 14f, 14f, strokePaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 14f
    textPaint.color = muted
    canvas.drawText(data.labelHeatmap.ifBlank { "12-WEEK ACTIVITY" }.uppercase(), rightX + 22f, 240f, textPaint)

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
        fillPaint.color = when {
            cnt <= 0 -> withAlpha(line, 80)
            cnt <= maxDay / 4 -> withAlpha(accent, 90)
            cnt <= maxDay / 2 -> withAlpha(accent, 145)
            cnt <= (maxDay * 3) / 4 -> withAlpha(accent, 200)
            else -> accent
        }
        val cx = hmX + c * (cW + hGap)
        val cy = hmY + r * (cH + hGap)
        canvas.drawRoundRect(RectF(cx, cy, cx + cW, cy + cH), 4f, 4f, fillPaint)
    }

    // Top Decks panel (y: 446..626)
    val deckRect = RectF(rightX, 446f, rightX + rightW, 626f)
    fillPaint.color = panel
    canvas.drawRoundRect(deckRect, 14f, 14f, fillPaint)
    strokePaint.color = withAlpha(line, 155)
    canvas.drawRoundRect(deckRect, 14f, 14f, strokePaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 14f
    textPaint.color = muted
    canvas.drawText(data.labelDecks.ifBlank { "ACTIVE DECKS" }.uppercase(), rightX + 22f, 476f, textPaint)

    val decks = data.topDecks.take(3)
    decks.forEachIndexed { idx, deck ->
        val dy = 508f + idx * 40f
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.textSize = 15f
        textPaint.color = ink
        val countText = "${deck.known}/${deck.total}"
        val cw = textPaint.measureText(countText)
        canvas.drawText(ellipsize(textPaint, deck.title, rightW - 60f - cw), rightX + 22f, dy, textPaint)
        textPaint.color = muted
        canvas.drawText(countText, rightX + rightW - 22f - cw, dy, textPaint)

        val frac = if (deck.total > 0) (deck.known.toFloat() / deck.total.toFloat()).coerceIn(0f, 1f) else 0f
        val dBarY = dy + 8f
        val dBarW = rightW - 44f
        fillPaint.color = withAlpha(line, 90)
        canvas.drawRoundRect(RectF(rightX + 22f, dBarY, rightX + 22f + dBarW, dBarY + 6f), 3f, 3f, fillPaint)
        if (frac > 0f) {
            fillPaint.color = accent
            canvas.drawRoundRect(RectF(rightX + 22f, dBarY, rightX + 22f + dBarW * frac, dBarY + 6f), 3f, 3f, fillPaint)
        }
    }

    // Footer
    strokePaint.color = withAlpha(line, 120)
    canvas.drawLine(64f, (h - 62).toFloat(), (w - 64).toFloat(), (h - 62).toFloat(), strokePaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 15f
    textPaint.color = muted
    canvas.drawText(data.footerText, 64f, (h - 28).toFloat(), textPaint)

    val rw = textPaint.measureText(data.recoveredText)
    textPaint.color = accent
    canvas.drawText(data.recoveredText, w - 64f - rw, (h - 28).toFloat(), textPaint)

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
}

private fun drawAndroidAvatarBadge(
    canvas: Canvas,
    x: Float,
    y: Float,
    size: Float,
    data: ProfileStatsCardData,
    bg: Int,
    panel: Int,
    line: Int,
    ink: Int,
    muted: Int,
    accent: Int
) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = panel }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(line, 180) }
    val rect = RectF(x, y, x + size, y + size)
    canvas.drawRoundRect(rect, 14f, 14f, fill)

    val customBitmap = data.profileAvatar.takeIf { it.isNotBlank() }?.let { path ->
        runCatching {
            val f = File(path)
            if (f.isFile) BitmapFactory.decodeFile(f.absolutePath) else null
        }.getOrNull()
    }
    if (customBitmap != null) {
        try {
            val clipPath = Path().apply { addRoundRect(rect, 14f, 14f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            val side = minOf(customBitmap.width, customBitmap.height)
            val sx = (customBitmap.width - side) / 2
            val sy = (customBitmap.height - side) / 2
            val src = Rect(sx, sy, sx + side, sy + side)
            canvas.drawBitmap(customBitmap, src, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } finally {
            customBitmap.recycle()
        }
        canvas.drawRoundRect(rect, 14f, 14f, stroke)
        return
    }

    canvas.drawRoundRect(rect, 14f, 14f, stroke)
    val pad = size * 0.20f
    val cell = (size - pad * 2f) / 7f
    val ox = x + (size - cell * 6f) / 2f
    val oy = y + (size - cell * 7f) / 2f
    for (r in PROFILE_CARD_EMBLEM.indices) {
        val row = PROFILE_CARD_EMBLEM[r]
        for (c in row.indices) {
            val ch = row[c]
            if (ch == '.') continue
            fill.color = if (ch == 'A') accent else ink
            canvas.drawRoundRect(
                RectF(ox + c * cell, oy + r * cell, ox + (c + 1) * cell - 1.5f, oy + (r + 1) * cell - 1.5f),
                2f, 2f, fill
            )
        }
    }
}

private fun drawAndroidBackdropEmblem(canvas: Canvas, w: Int, h: Int, bg: Int, ink: Int, accent: Int) {
    val luma = (0.2126 * Color.red(bg) + 0.7152 * Color.green(bg) + 0.0722 * Color.blue(bg)) / 255.0
    val inkCell = blendArgb(bg, ink, if (luma < 0.01) 0.14f else 0.075f)
    val accCell = blendArgb(bg, accent, if (luma < 0.01) 0.24f else 0.16f)
    val paint = Paint().apply { style = Paint.Style.FILL }

    canvas.save()
    canvas.rotate(-14f, w / 2f, h / 2f)
    val cell = 9f
    val pitchX = 160f
    val pitchY = 148f
    for (row in -2..7) {
        val stagger = if (row % 2 == 0) 0f else pitchX / 2f
        val baseY = row * pitchY - 80f
        for (col in -2..10) {
            val baseX = col * pitchX + stagger - 80f
            for (r in PROFILE_CARD_EMBLEM.indices) {
                val pattern = PROFILE_CARD_EMBLEM[r]
                for (c in pattern.indices) {
                    val ch = pattern[c]
                    if (ch == '.') continue
                    paint.color = if (ch == 'A') accCell else inkCell
                    canvas.drawRect(
                        baseX + c * cell,
                        baseY + r * cell,
                        baseX + (c + 1) * cell,
                        baseY + (r + 1) * cell,
                        paint
                    )
                }
            }
        }
    }
    canvas.restore()
}

private fun withAlpha(argb: Int, alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(argb), Color.green(argb), Color.blue(argb))

private fun blendArgb(a: Int, b: Int, t: Float): Int = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
)

private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var s = text
    while (s.length > 3 && paint.measureText("$s…") > maxWidth) {
        s = s.dropLast(1)
    }
    return "$s…"
}
