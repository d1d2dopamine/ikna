package dev.ikna.ui.search
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import androidx.compose.ui.text.style.TextDecoration
import dev.ikna.data.catalog.catalogMeaning
import dev.ikna.data.db.ChunkSearchRow

@Composable
fun IknaSearchResult(
    row: ChunkSearchRow,
    onOpenDeck: () -> Unit,
    onSource: (String) -> Unit
) {
    val meaning = catalogMeaning(row.translation)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = row.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = row.contextSentence,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = meaning.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        meaning.tatoebaId?.let { id ->
            Spacer(Modifier.height(Space.xs))
            Text(
                text = S.t("src.001") + "Tatoeba #" + id,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onSource(id) }
            )
        }
        Spacer(Modifier.height(Space.sm))
        IknaTextButton(
            label = S.t("search.010") + row.packTitle,
            onClick = onOpenDeck,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
