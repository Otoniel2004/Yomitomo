package io.github.otoniel2004.mangareaderjp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.srs.Rating
import io.github.otoniel2004.mangareaderjp.srs.Srs

/**
 * The app's "Anki": front = Japanese; back = romaji + meaning/translation.
 *
 *   [front] → "Show answer" → [back + 4 buttons] → schedules the next review
 *
 * Furigana on the front follows the item's own toggle.
 */
@Composable
fun Flashcard(
    item: SavedItem,
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hide the answer again whenever the card changes or gets rated.
    var revealed by remember(item.id, item.srs.lastReviewedAt) { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FuriganaText(
                segments = item.segments,
                showFurigana = item.showFurigana,
                fontSize = if (item.type == ItemType.SENTENCE) 24.sp else 34.sp,
                colorByPart = true,
            )

            if (!revealed) {
                Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show answer")
                }
            } else {
                HorizontalDivider()
                RomajiLine(item.segments)
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                RatingButtons(item, onRate)
            }
        }
    }
}

@Composable
private fun RatingButtons(item: SavedItem, onRate: (Rating) -> Unit) {
    val options = listOf(
        Rating.AGAIN to "Again",
        Rating.HARD to "Hard",
        Rating.GOOD to "Good",
        Rating.EASY to "Easy",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for ((rating, label) in options) {
            val content: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, maxLines = 1)
                    Text(Srs.previewLabel(item.srs, rating), fontSize = 11.sp, maxLines = 1)
                }
            }
            val buttonModifier = Modifier.weight(1f)
            val padding = ButtonDefaults.TextButtonContentPadding
            if (rating == Rating.GOOD) {
                Button(onClick = { onRate(rating) }, modifier = buttonModifier, contentPadding = padding) { content() }
            } else {
                OutlinedButton(onClick = { onRate(rating) }, modifier = buttonModifier, contentPadding = padding) { content() }
            }
        }
    }
}
