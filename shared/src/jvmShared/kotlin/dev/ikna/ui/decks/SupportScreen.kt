package dev.ikna.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaAppearanceVariant
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*

private const val REPOSITORY = "https://github.com/d1d2dopamine/ikna"

private enum class SupportDestination(val label: String, val url: String) {
    REPO("support.repo", REPOSITORY),
    ISSUE("support.issue", "$REPOSITORY/issues/new/choose"),
    MAIL("support.mail", "mailto:d1d2dopamine@gmail.com"),
    AUTHOR("support.author", "https://github.com/d1d2dopamine")
}

@Composable
fun IknaSupportScreen(onBack: () -> Unit) {
    val handler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = Edge, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text(S.t("support.title"), style = MaterialTheme.typography.headlineSmall)
            Text(S.t("support.body"), modifier = Modifier.widthIn(max = 560.dp),
                style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.Top
            ) {
                for (destination in SupportDestination.entries) {
                    SupportLinkTile(destination) {
                        failed = runCatching { handler.openUri(destination.url) }.isFailure
                    }
                }
            }
            if (failed) Text(S.t("support.open_failed"), style = MaterialTheme.typography.bodySmall)
        }
        IknaBottomBar {
            IknaIconButton(IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}

@Composable
private fun SupportLinkTile(destination: SupportDestination, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaControlShape()
    Column(
        modifier = Modifier.width(68.dp).heightIn(min = 72.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xs, vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterVertically)
    ) {
        SupportLinkIcon(destination)
        Text(S.t(destination.label), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SupportLinkIcon(destination: SupportDestination) {
    val ink = MaterialTheme.colorScheme.primary
    val rounded = LocalIknaAppearanceVariant.current == IknaAppearanceVariant.ROUNDED
    Canvas(Modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(
            width = 1.7f * unit,
            cap = if (rounded) StrokeCap.Round else StrokeCap.Butt,
            join = if (rounded) StrokeJoin.Round else StrokeJoin.Miter
        )
        val corner = CornerRadius(if (rounded) 2.5f * unit else 0f)
        fun outline(x: Float, y: Float, w: Float, h: Float) {
            drawRoundRect(ink, point(x, y), Size(w * unit, h * unit), corner, style = stroke)
        }
        fun line(x: Float, y: Float, endX: Float, endY: Float) {
            drawLine(ink, point(x, y), point(endX, endY), stroke.width, stroke.cap)
        }
        when (destination) {
            SupportDestination.REPO -> {
                outline(4f, 3f, 16f, 18f)
                line(8f, 3f, 8f, 21f)
                line(11f, 8f, 17f, 8f)
                line(11f, 12f, 16f, 12f)
            }
            SupportDestination.ISSUE -> {
                drawCircle(ink, 9f * unit, style = stroke)
                line(12f, 6.5f, 12f, 12.5f)
                drawCircle(ink, unit, point(12f, 16.5f))
            }
            SupportDestination.MAIL -> {
                outline(3f, 5f, 18f, 14f)
                val fold = Path().apply {
                    moveTo(3.5f * unit, 6f * unit)
                    lineTo(12f * unit, 12.5f * unit)
                    lineTo(20.5f * unit, 6f * unit)
                }
                drawPath(fold, ink, style = stroke)
            }
            SupportDestination.AUTHOR -> {
                drawCircle(ink, 4f * unit, point(12f, 7.5f), style = stroke)
                drawArc(ink, 180f, 180f, false, point(4f, 14f), Size(16f * unit, 13f * unit), style = stroke)
            }
        }
    }
}
