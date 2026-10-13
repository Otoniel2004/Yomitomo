package io.github.otoniel2004.mangareaderjp.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.Segment
import io.github.otoniel2004.mangareaderjp.reader.page.PageWord
import io.github.otoniel2004.mangareaderjp.ui.components.FuriganaText
import io.github.otoniel2004.mangareaderjp.ui.components.RomajiLine
import io.github.otoniel2004.mangareaderjp.ui.components.label
import io.github.otoniel2004.mangareaderjp.ui.home.label
import io.github.otoniel2004.mangareaderjp.ui.theme.MangaReaderTheme

/**
 * The card shown over the manga. It goes to the half of the screen away from the tapped
 * text ([atTop]), so the text stays visible. A dim tap anywhere outside closes it.
 */
@Composable
fun PopupFrame(atTop: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    MangaReaderTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        ) {
            Card(
                Modifier
                    .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 56.dp)
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .pointerInput(Unit) { detectTapGestures { } }, // taps on the card don't close it
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { content() }
            }
        }
    }
}

/** 1 tap: one word, with reading, romaji, meanings and Save. */
@Composable
fun WordPopup(word: PageWord, atTop: Boolean, onSave: () -> Unit, onClose: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    PopupFrame(atTop, onClose) {
        val pos = word.segments.firstOrNull()?.pos?.label().orEmpty()
        Text(
            listOf(word.type.label(), pos).filter(String::isNotEmpty).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FuriganaText(word.segments, showFurigana = true, fontSize = 32.sp)
        RomajiLine(word.segments)

        val entry = word.entry
        if (entry != null) {
            val dictionaryForm = entry.preferredForm
            if (dictionaryForm != word.text) {
                Text("Dictionary form: $dictionaryForm (${entry.readings.first()})", style = MaterialTheme.typography.bodyMedium)
            }
            entry.meanings().forEachIndexed { i, meaning ->
                Text("${i + 1}. $meaning", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text("Not found in the dictionary.", style = MaterialTheme.typography.bodyMedium)
        }

        SaveRow(saved, label = "Save word", onClose) {
            saved = true
            onSave()
        }
    }
}

/** 2 taps: the whole bubble, with particles highlighted. */
@Composable
fun SentencePopup(segments: List<Segment>, atTop: Boolean, onSave: () -> Unit, onClose: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    PopupFrame(atTop, onClose) {
        Text(ItemType.SENTENCE.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        FuriganaText(segments, showFurigana = true, fontSize = 24.sp, highlightParticles = true)
        RomajiLine(segments)
        Text(
            "English translation of the whole bubble arrives with DeepL in Phase 5.",
            style = MaterialTheme.typography.bodySmall,
        )
        SaveRow(saved, label = "Save sentence", onClose) {
            saved = true
            onSave()
        }
    }
}

@Composable
private fun SaveRow(saved: Boolean, label: String, onClose: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSave, enabled = !saved) { Text(if (saved) "Saved ✓" else label) }
        TextButton(onClick = onClose) { Text("Close") }
    }
}
