package dev.ikna.ui.stats

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

actual fun renderProfileStatsCardPng(data: ProfileStatsCardData): ByteArray {
    val w = 1200
    val h = 675
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bg = data.bgArgb
    val panel = data.panelArgb
    val line = data.lineArgb
    val ink = data.inkArgb
    val muted = data.mutedArgb
    val accent = data.accentArgb

    // Base fill
    canvas.drawColor(bg)

    // Subtle tilted Ikna pixel-emblem backdrop
    drawAndroidBackdropEmblem(canvas, w, h, bg, ink, accent)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Top eyebrow + accent bar
    fillPaint.color = accent
    canvas.drawRoundRect(RectF(68f, 56f, 112f, 60f), 4f, 4f, fillPaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 17f
    textPaint.color = accent
    canvas.drawText(data.headerText, 126f, 64f, textPaint)

    textPaint.color = muted
    val dateW = textPaint.measureText(data.generatedDate)
    canvas.drawText(data.generatedDate, w - 68f - dateW, 64f, textPaint)

    // Profile name + subtitle
    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 48f
    textPaint.color = ink
    canvas.drawText(data.profileName.take(28), 68f, 134f, textPaint)

    textPaint.typeface = Typeface.SANS_SERIF
    textPaint.textSize = 22f
    textPaint.color = muted
    canvas.drawText(data.subtitle, 68f, 170f, textPaint)

    // Divider
    strokePaint.color = withAlpha(line, 140)
    canvas.drawLine(68f, 200f, (w - 68).toFloat(), 200f, strokePaint)

    // 4 metric tiles in 2x2 grid
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
    val gap = 24f
    val tileW = (w - 136f - gap) / 2f
    val tileH = 162f
    val startY = 232f

    tiles.forEachIndexed { idx, (value, label, highlight) ->
        val col = idx % 2
        val row = idx / 2
        val x = 68f + col * (tileW + gap)
        val y = startY + row * (tileH + gap)
        val rect = RectF(x, y, x + tileW, y + tileH)

        fillPaint.color = panel
        canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
        strokePaint.color = withAlpha(line, 155)
        canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

        fillPaint.color = if (highlight) accent else withAlpha(muted, 170)
        canvas.drawRoundRect(RectF(x + 26f, y + 30f, x + 30f, y + 74f), 4f, 4f, fillPaint)

        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 46f
        textPaint.color = ink
        canvas.drawText(value, x + 46f, y + 72f, textPaint)

        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.textSize = 19f
        textPaint.color = muted
        canvas.drawText(ellipsize(textPaint, label, tileW - 56f), x + 28f, y + 124f, textPaint)
    }

    // Footer
    strokePaint.color = withAlpha(line, 120)
    canvas.drawLine(68f, (h - 72).toFloat(), (w - 68).toFloat(), (h - 72).toFloat(), strokePaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 15f
    textPaint.color = muted
    canvas.drawText(data.footerText, 68f, (h - 38).toFloat(), textPaint)

    val recoveredNote = data.recoveredText
    val rw = textPaint.measureText(recoveredNote)
    textPaint.color = accent
    canvas.drawText(recoveredNote, w - 68f - rw, (h - 38).toFloat(), textPaint)

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
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
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
        end--
    }
    return text.substring(0, end) + "…"
}
