package io.github.otoniel2004.mangareaderjp.reader.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import io.github.otoniel2004.mangareaderjp.ui.components.PartOfSpeechLegend
import io.github.otoniel2004.mangareaderjp.ui.components.RomajiLine
import io.github.otoniel2004.mangareaderjp.ui.components.label
import io.github.otoniel2004.mangareaderjp.ui.home.label
import io.github.otoniel2004.mangareaderjp.translation.BubbleTranslations
import io.github.otoniel2004.mangareaderjp.translation.TranslationState
import io.github.otoniel2004.mangareaderjp.ui.theme.MangaReaderTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * The popup over the manga. Two views of the same bubble, switchable with a button:
 *
 *   1 tap  → word view      ── "Whole sentence ›" ──►  sentence view
 *   2 taps → sentence view  ◄── "‹ Back to word" ──    (only if opened from a word)
 *
 * It goes to the half of the screen away from the tapped text ([atTop]); a tap on the dim
 * area outside closes it.
 */
@Composable
fun ReaderPopup(
    word: PageWord?,
    sentence: List<Segment>,
    sourceText: String,
    translations: StateFlow<Map<String, BubbleTranslations>>,
    atTop: Boolean,
    onSaveWord: (PageWord) -> Unit,
    onSaveSentence: () -> Unit,
    onNeedTranslation: () -> Unit,
    onClose: () -> Unit,
) {
    val translationMap by translations.collectAsState()
    val translation = translationMap[sourceText]

    var showSentence by remember { mutableStateOf(word == null) }
    var wordSaved by remember { mutableStateOf(false) }
    var sentenceSaved by remember { mutableStateOf(false) }

    PopupFrame(atTop, onClose) {
        if (word == null || showSentence) {
            SentenceView(
                segments = sentence,
                translation = translation,
                onNeedTranslation = onNeedTranslation,
                saved = sentenceSaved,
                onSave = { sentenceSaved = true; onSaveSentence() },
                onBack = if (word != null) ({ showSentence = false }) else null,
                onClose = onClose,
            )
        } else {
            WordView(
                word = word,
                saved = wordSaved,
                onSave = { wordSaved = true; onSaveWord(word) },
                onShowSentence = { showSentence = true },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PopupFrame(atTop: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
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
                    .heightIn(max = 460.dp)
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

@Composable
private fun WordView(word: PageWord, saved: Boolean, onSave: () -> Unit, onShowSentence: () -> Unit, onClose: () -> Unit) {
    val pos = word.segments.firstOrNull()?.pos?.label().orEmpty()
    Text(
        listOf(word.type.label(), pos).filter(String::isNotEmpty).joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    FuriganaText(word.segments, showFurigana = true, fontSize = 32.sp, colorByPart = true)
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

    Actions {
        Button(onClick = onSave, enabled = !saved) { Text(if (saved) "Saved ✓" else "Save word") }
        OutlinedButton(onClick = onShowSentence) { Text("Whole sentence ›") }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun SentenceView(
    segments: List<Segment>,
    translation: BubbleTranslations?,
    onNeedTranslation: () -> Unit,
    saved: Boolean,
    onSave: () -> Unit,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Text(ItemType.SENTENCE.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    FuriganaText(segments, showFurigana = true, fontSize = 24.sp, colorByPart = true)
    PartOfSpeechLegend(segments)
    RomajiLine(segments)
    // Ask once when the sentence appears (instant if the page was already translated).
    LaunchedEffect(Unit) { onNeedTranslation() }
    HorizontalDivider()
    // DeepL first (best quality), then the on-device one. Errors are not shown: whichever
    // translation worked is enough; only if both fail does the popup say so.
    TranslationLine("DeepL", translation?.deepL, loadingText = "Translating…")
    TranslationLine("On-device", translation?.offline, loadingText = "Translating (the first time also downloads the offline model)…")
    val deepLFailed = translation?.deepL.let { it == null || it is TranslationState.Failed }
    if (deepLFailed && translation?.offline is TranslationState.Failed) {
        Text("Translation unavailable right now.", style = MaterialTheme.typography.bodyMedium)
    }
    Actions {
        Button(onClick = onSave, enabled = !saved) { Text(if (saved) "Saved ✓" else "Save sentence") }
        if (onBack != null) OutlinedButton(onClick = onBack) { Text("‹ Back to word") }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun Actions(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** One labelled translation: the text when done, a spinner while loading, nothing if it failed. */
@Composable
private fun TranslationLine(label: String, state: TranslationState?, loadingText: String) {
    when (state) {
        is TranslationState.Done -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(state.english, style = MaterialTheme.typography.titleMedium)
        }
        TranslationState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("$label: $loadingText", style = MaterialTheme.typography.bodySmall)
        }
        is TranslationState.Failed, null -> Unit
    }
}
