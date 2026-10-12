package dev.ikna.desktop
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

@Composable
fun DesktopPaneFrame(title: String, onBack: () -> Unit,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds()) {
        Column(Modifier.fillMaxSize().padding(bottom = BarHeight)) {
            Row(Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = 40.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = titleStyle, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            }
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
        IknaBottomBar(Modifier.align(Alignment.BottomCenter), textured = true) {
            IknaIconButton(IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}

@Composable
fun DesktopScrollablePane(title: String, onBack: () -> Unit,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    readingWidth: Dp = 760.dp, content: @Composable ColumnScope.() -> Unit) {
    DesktopPaneFrame(title, onBack, titleStyle) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp)) {
            Spacer(Modifier.height(Space.md))
            // The reading measure centres in the pane instead of hugging the
            // deck list: on a wide window the empty side is intentional space.
            Column(Modifier.widthIn(max = readingWidth).fillMaxWidth().align(Alignment.CenterHorizontally), content = content)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/** The same frame/reading measure as DesktopScrollablePane, with one bounded lazy viewport. */
@Composable
fun DesktopLazyPane(title: String, onBack: () -> Unit,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    readingWidth: Dp = 760.dp, content: LazyListScope.() -> Unit) {
    DesktopPaneFrame(title, onBack, titleStyle) {
        Box(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
            LazyColumn(
                modifier = Modifier.widthIn(max = readingWidth).fillMaxWidth().fillMaxHeight()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(top = Space.md, bottom = Space.xxl),
                content = content
            )
        }
    }
}
