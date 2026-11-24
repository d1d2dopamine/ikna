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
        strokeWidth = 1f
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    val marginX = 68f
    val rightEdge = w - marginX

    // 1. Open Editorial Header: circular avatar + identity + single date stamp top-right
    drawAndroidAvatarCircle(canvas, marginX, 54f, 76f, data, panel, line, ink, accent)

    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 36f
    textPaint.color = ink
    canvas.drawText(ellipsize(textPaint, data.profileName, 700f), 166f, 92f, textPaint)

    val subLine = data.profileBio.ifBlank { data.periodLabel }
    if (subLine.isNotBlank()) {
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.textSize = 18f
        textPaint.color = muted
        canvas.drawText(ellipsize(textPaint, subLine, 720f), 166f, 122f, textPaint)
    }

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 14f
    textPaint.color = muted
    val metaText = if (data.generatedDate.isNotBlank()) "ikna · ${data.generatedDate}" else "ikna"
    val metaW = textPaint.measureText(metaText)
    canvas.drawText(metaText, rightEdge - metaW, 88f, textPaint)

    // Hairline 1
    strokePaint.color = withAlpha(line, 125)
    canvas.drawLine(marginX, 160f, rightEdge, 160f, strokePaint)

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
        AndroidKpiEntry(
            value = data.matureCards.toString(),
            label = data.labelMature.ifBlank { data.labelTargets },
            detail = stabDetail,
            emphasized = data.matureCards > 0
        ),
        AndroidKpiEntry(
            value = data.recallPercentText,
            label = data.labelRecall,
            detail = recallDetail,
            emphasized = data.repeatAnswers >= 5
        ),
        AndroidKpiEntry(
            value = data.studyTimeText,
            label = data.labelStudyTime.ifBlank { data.labelAnswers },
            detail = timeDetail,
            emphasized = false
        ),
        AndroidKpiEntry(
            value = "${data.activeDays30d}/30",
            label = data.labelActiveDays.ifBlank { data.periodLabel },
            detail = "",
            emphasized = false
        )
    )

    kpis.forEachIndexed { idx, item ->
        val cx = marginX + idx * (colW + colGap)
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 40f
        textPaint.color = if (item.emphasized) accent else ink
        canvas.drawText(item.value, cx, 232f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 15f
        textPaint.color = ink
        canvas.drawText(ellipsize(textPaint, item.label, colW), cx, 262f, textPaint)

        if (item.detail.isNotBlank()) {
            textPaint.typeface = Typeface.MONOSPACE
            textPaint.textSize = 13f
            textPaint.color = muted
            canvas.drawText(ellipsize(textPaint, item.detail, colW), cx, 286f, textPaint)
        }
    }

    // Hairline 2
    strokePaint.color = withAlpha(line, 125)
    canvas.drawLine(marginX, 316f, rightEdge, 316f, strokePaint)

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

    val activeCountStr = "$activeDays84d / 84 ${data.unitDay}"
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 13f
    textPaint.color = muted
    val activeCountW = textPaint.measureText(activeCountStr)
    canvas.drawText(activeCountStr, hmX + gridW - activeCountW, 366f, textPaint)

    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 16f
    textPaint.color = ink
    canvas.drawText(
        ellipsize(textPaint, data.labelHeatmap.ifBlank { "12-week activity" }, gridW - activeCountW - 14f),
        hmX,
        366f,
        textPaint
    )

    for (i in 0 until 84) {
        val c = i / 7
        val r = i % 7
        val cnt = days[i]
        fillPaint.color = when {
            cnt <= 0 -> withAlpha(line, 92)
            cnt <= maxDay / 4 -> withAlpha(accent, 95)
            cnt <= maxDay / 2 -> withAlpha(accent, 150)
            cnt <= (maxDay * 3) / 4 -> withAlpha(accent, 205)
            else -> accent
        }
        val cx = hmX + c * (cellSize + cellGap)
        val cy = hmY + r * (cellSize + cellGap)
        canvas.drawRoundRect(RectF(cx, cy, cx + cellSize, cy + cellSize), 4f, 4f, fillPaint)
    }

    // RIGHT: Memory Maturity + Calm Active Decks (zero 0/3000 denominators)
    val rightX = 562f
    val rightW = rightEdge - rightX

    val studied = (data.matureCards + data.youngCards + data.learningCards).coerceAtLeast(0)
    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 16f
    textPaint.color = ink
    canvas.drawText(data.labelMaturity.ifBlank { "Memory maturity" }, rightX, 366f, textPaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 14f
    textPaint.color = muted
    val totStr = studied.toString()
    canvas.drawText(totStr, rightEdge - textPaint.measureText(totStr), 366f, textPaint)

    val barY = 390f
    val barH = 8f
    val barRect = RectF(rightX, barY, rightEdge, barY + barH)
    val barPath = Path().apply { addRoundRect(barRect, 4f, 4f, Path.Direction.CW) }
    canvas.save()
    canvas.clipPath(barPath)
    fillPaint.color = withAlpha(line, 92)
    canvas.drawRect(barRect, fillPaint)
    if (studied > 0) {
        val mW = rightW * (data.matureCards.toFloat() / studied)
        val yW = rightW * (data.youngCards.toFloat() / studied)
        val lW = (rightW - mW - yW).coerceAtLeast(0f)
        if (mW > 0f) {
            fillPaint.color = accent
            canvas.drawRect(rightX, barY, rightX + mW, barY + barH, fillPaint)
        }
        if (yW > 0f) {
            fillPaint.color = withAlpha(accent, 140)
            canvas.drawRect(rightX + mW, barY, rightX + mW + yW, barY + barH, fillPaint)
        }
        if (lW > 0f) {
            fillPaint.color = withAlpha(muted, 115)
            canvas.drawRect(rightX + mW + yW, barY, rightEdge, barY + barH, fillPaint)
        }
    }
    canvas.restore()

    // Single-line measured dot legend (never wraps or collides)
    val legendItems = listOf(
        Triple(accent, data.labelMature.ifBlank { "Mature" }, data.matureCards),
        Triple(withAlpha(accent, 140), data.labelYoung.ifBlank { "Young" }, data.youngCards),
        Triple(withAlpha(muted, 115), data.labelLearning.ifBlank { "Learning" }, data.learningCards)
    )
    textPaint.typeface = Typeface.SANS_SERIF
    textPaint.textSize = 14f
    var legX = rightX
    val legBaseline = 424f
    legendItems.forEach { (dotColor, label, count) ->
        val txt = "$label $count"
        val itemW = 7f + 8f + textPaint.measureText(txt)
        if (legX + itemW <= rightEdge + 4f) {
            fillPaint.color = dotColor
            canvas.drawCircle(legX + 3.5f, legBaseline - 5.5f, 3.5f, fillPaint)
            textPaint.color = muted
            canvas.drawText(txt, legX + 15f, legBaseline, textPaint)
            legX += itemW + 24f
        }
    }

    // Subtle divider between Maturity and Decks
    strokePaint.color = withAlpha(line, 100)
    canvas.drawLine(rightX, 454f, rightEdge, 454f, strokePaint)

    // Active Decks (calm positive progress only)
    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 16f
    textPaint.color = ink
    canvas.drawText(data.labelDecks.ifBlank { "Decks" }, rightX, 492f, textPaint)

    val decks = data.topDecks.take(3)
    decks.forEachIndexed { idx, deck ->
        val dy = 532f + idx * 44f
        val statusText = formatDeckProgressQuiet(
            deck = deck,
            matureFmt = data.labelDeckMatureFmt,
            studiedFmt = data.labelDeckStudiedFmt,
            readyLabel = data.labelDeckReady
        )
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 13f
        val statusW = if (statusText.isNotBlank()) textPaint.measureText(statusText) else 0f
        if (statusText.isNotBlank()) {
            textPaint.color = if (deck.known > 0) accent else muted
            canvas.drawText(statusText, rightEdge - statusW, dy, textPaint)
        }

        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.textSize = 15f
        textPaint.color = ink
        val titleMaxW = (rightW - statusW - 20f).coerceAtLeast(120f)
        canvas.drawText(ellipsize(textPaint, deck.title, titleMaxW), rightX, dy, textPaint)

        if (idx < decks.lastIndex) {
            strokePaint.color = withAlpha(line, 65)
            canvas.drawLine(rightX, dy + 16f, rightEdge, dy + 16f, strokePaint)
        }
    }

    // 4. Minimal Footer (hairline + quiet wordmark; recovered only when > 0)
    strokePaint.color = withAlpha(line, 95)
    canvas.drawLine(marginX, (h - 54).toFloat(), rightEdge, (h - 54).toFloat(), strokePaint)

    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 14f
    textPaint.color = withAlpha(muted, 195)
    canvas.drawText("ikna", marginX, (h - 24).toFloat(), textPaint)

    if (data.recoveredCount > 0 && data.recoveredText.isNotBlank()) {
        val rw = textPaint.measureText(data.recoveredText)
        textPaint.color = accent
        canvas.drawText(data.recoveredText, rightEdge - rw, (h - 24).toFloat(), textPaint)
    }

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
}

private data class AndroidKpiEntry(
    val value: String,
    val label: String,
    val detail: String,
    val emphasized: Boolean
)

private fun drawAndroidAvatarCircle(
    canvas: Canvas,
    x: Float,
    y: Float,
    size: Float,
    data: ProfileStatsCardData,
    panel: Int,
    line: Int,
    ink: Int,
    accent: Int
) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = panel }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = withAlpha(line, 165)
    }
    val rect = RectF(x, y, x + size, y + size)
    val radius = size / 2f
    canvas.drawCircle(x + radius, y + radius, radius, fill)

    val customBitmap = data.profileAvatar.takeIf { it.isNotBlank() }?.let { path ->
        runCatching {
            val f = File(path)
            if (f.isFile) BitmapFactory.decodeFile(f.absolutePath) else null
        }.getOrNull()
    }
    if (customBitmap != null) {
        try {
            val clipPath = Path().apply { addOval(rect, Path.Direction.CW) }
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
        canvas.drawCircle(x + radius, y + radius, radius, stroke)
        return
    }

    canvas.drawCircle(x + radius, y + radius, radius, stroke)
    val pad = size * 0.24f
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
                RectF(ox + c * cell, oy + r * cell, ox + (c + 1) * cell - 1.2f, oy + (r + 1) * cell - 1.2f),
                2f, 2f, fill
            )
        }
    }
}

private fun drawAndroidBackdropEmblem(canvas: Canvas, w: Int, h: Int, bg: Int, ink: Int, accent: Int) {
    val luma = (0.2126 * Color.red(bg) + 0.7152 * Color.green(bg) + 0.0722 * Color.blue(bg)) / 255.0
    val inkCell = blendArgb(bg, ink, if (luma < 0.01) 0.11f else 0.06f)
    val accCell = blendArgb(bg, accent, if (luma < 0.01) 0.18f else 0.12f)
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
