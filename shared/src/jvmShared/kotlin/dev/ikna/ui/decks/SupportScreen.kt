package dev.ikna.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.data.update.UpdateCheck
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*

private const val REPOSITORY = "https://github.com/d1d2dopamine/ikna"

private enum class SupportDestination(val label: String, val url: String, val icon: SupportBrandIcon) {
    REPO("support.repo", REPOSITORY, SupportBrandIcon.REPOSITORY),
    ISSUE("support.issue", "$REPOSITORY/issues/new/choose", SupportBrandIcon.ISSUE),
    MAIL("support.mail", "mailto:d1d2dopamine@gmail.com", SupportBrandIcon.GMAIL),
    AUTHOR("support.author", "https://github.com/d1d2dopamine", SupportBrandIcon.AUTHOR)
}

private data class SupportPillar(
    val glyph: IknaGlyph,
    val titleKey: String,
    val bodyKey: String
)

private val SUPPORT_PILLARS = listOf(
    SupportPillar(IknaGlyph.LOCAL, "support.pillar_local_title", "support.pillar_local_body"),
    SupportPillar(IknaGlyph.PRIVACY, "support.pillar_private_title", "support.pillar_private_body"),
    SupportPillar(IknaGlyph.MEMORY, "support.pillar_engine_title", "support.pillar_engine_body")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IknaSupportScreen(installedVersion: String, onBack: () -> Unit) {
    val handler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    val unifiedFooter = LocalIknaUnifiedFooter.current
    val colors = MaterialTheme.colorScheme
    val cardFill = lerp(colors.surface, colors.onSurface, 0.04f)
    val cardBorder = colors.outline.copy(alpha = 0.34f)
    val cardShape = iknaCardShape()
    val badgeShape = iknaControlShape()

    Box(Modifier.fillMaxSize().background(colors.background)) {
        IknaBrandPatternBackdrop(intensity = 0.72f)
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Edge, vertical = Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        IknaWordmark(height = 32.dp)
                        Text(
                            text = S.t("support.description"),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterVertically)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(badgeShape)
                                    .background(cardFill, badgeShape)
                                    .border(1.dp, cardBorder, badgeShape)
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = S.t("upd.011") + installedVersion,
                                    style = iknaNumberStyle(MaterialTheme.typography.labelMedium),
                                    color = colors.onSurfaceVariant
                                )
                            }
                            IknaTextButton(S.t("support.changelog"), onClick = {
                                failed = runCatching { handler.openUri(UpdateCheck.RELEASES_PAGE) }.isFailure
                            })
                        }
                    }

                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth >= 560.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(Space.md)
                            ) {
                                for (pillar in SUPPORT_PILLARS) {
                                    SupportPillarCard(
                                        pillar = pillar,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Space.sm)
                            ) {
                                for (pillar in SUPPORT_PILLARS) {
                                    SupportPillarCard(
                                        pillar = pillar,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(cardFill, cardShape)
                            .border(1.dp, cardBorder, cardShape)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = S.t("support.body"),
                            modifier = Modifier.widthIn(max = 580.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 380.dp) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                                ) {
                                    for (destination in SupportDestination.entries) {
                                        SupportLinkTile(
                                            destination = destination,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            failed = runCatching { handler.openUri(destination.url) }.isFailure
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                                ) {
                                    for (pair in SupportDestination.entries.chunked(2)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                                        ) {
                                            for (destination in pair) {
                                                SupportLinkTile(
                                                    destination = destination,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    failed = runCatching { handler.openUri(destination.url) }.isFailure
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (failed) {
                            Text(
                                text = S.t("support.open_failed"),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.error
                            )
                        }
                    }
                }
            }
            if (!unifiedFooter) {
                IknaBottomBar {
                    IknaIconButton(IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
                }
            }
        }
    }
}

@Composable
private fun SupportPillarCard(
    pillar: SupportPillar,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val cardShape = iknaCardShape()
    val badgeShape = iknaControlShape()
    val fill = lerp(colors.surface, colors.onSurface, 0.04f)
    Column(
        modifier = modifier
            .clip(cardShape)
            .background(fill, cardShape)
            .border(1.dp, colors.outline.copy(alpha = 0.34f), cardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(badgeShape)
                .background(colors.primary.copy(alpha = 0.13f), badgeShape),
            contentAlignment = Alignment.Center
        ) {
            IknaGlyphIcon(
                glyph = pillar.glyph,
                color = colors.primary,
                size = 16.dp
            )
        }
        Text(
            text = S.t(pillar.titleKey),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground
        )
        Text(
            text = S.t(pillar.bodyKey),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SupportLinkTile(
    destination: SupportDestination,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaControlShape()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val motionEnabled = LocalIknaMotionEnabled.current
    val scale = iknaPressScale(pressed, motionEnabled, pressedScale = 0.96f)
    val sound = LocalIknaSoundFeedback.current
    val fill = lerp(colors.surface, colors.onSurface, if (hovered || pressed) 0.09f else 0.05f)
    val border = if (hovered || pressed) colors.primary.copy(alpha = 0.65f) else colors.outline.copy(alpha = 0.38f)

    Column(
        modifier = modifier
            .heightIn(min = 76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, border, shape)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button
            ) {
                sound.play(IknaSoundCue.TAP)
                onClick()
            }
            .padding(horizontal = Space.sm, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterVertically)
    ) {
        SupportBrandMark(destination.icon)
        Text(
            text = S.t(destination.label),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface
        )
    }
}
