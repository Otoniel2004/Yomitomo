package io.github.otoniel2004.mangareaderjp.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.japanese.Chunk
import io.github.otoniel2004.mangareaderjp.ui.appViewModel
import io.github.otoniel2004.mangareaderjp.ui.components.CardImage
import io.github.otoniel2004.mangareaderjp.ui.components.FuriganaText
import io.github.otoniel2004.mangareaderjp.ui.components.RomajiLine
import io.github.otoniel2004.mangareaderjp.ui.home.label

/**
 * "New card" screen. Text comes from the keyboard (romaji or Japanese) or pre-filled from the camera.
 *
 *   [ input ]            ore wa zettai ni akiramenai
 *   [ word chips ]       俺 · は · 絶対 · に · 諦めない   ← tap one to pick another spelling
 *   [ preview ]          same look as a saved card (furigana + romaji)
 *   [ type / meanings / translation ]
 *   [ Save ]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    initialText: String,
    imagePath: String?,
    source: String,
    onDone: () -> Unit,
) {
    val viewModel = appViewModel(key = "add-$initialText-$imagePath") {
        AddItemViewModel(it, initialText, imagePath, source)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New card") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.imagePath?.let { CardImage(it) }

            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::setInput,
                label = { Text("Romaji or Japanese") },
                supportingText = {
                    Text("Separate words with spaces: ore wa zettai ni akiramenai. CAPITALS → katakana.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.languageReady) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Preparing the dictionary (only on the first launch)…", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.chunks.any { it.candidates.size > 1 }) {
                Text("Tap a word to change how it is written", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.chunks.forEachIndexed { index, chunk ->
                        ChunkChip(chunk) { candidate -> viewModel.selectCandidate(index, candidate) }
                    }
                }
            }

            if (state.tokens.count { it.pos != PartOfSpeech.PUNCTUATION } > 1) {
                Text(
                    "Tap words to make a card from just them (the whole text is kept as context)",
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val range = state.selectedRange
                    state.tokens.forEachIndexed { index, token ->
                        if (token.pos == PartOfSpeech.PUNCTUATION || token.surface.isBlank()) return@forEachIndexed
                        FilterChip(
                            selected = range != null && index in range,
                            onClick = { viewModel.toggleToken(index) },
                            label = { Text(token.surface, fontSize = 18.sp) },
                        )
                    }
                }
            }

            state.draft?.takeIf { it.text.isNotBlank() }?.let { draft ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.type.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        FuriganaText(
                            segments = draft.segments,
                            showFurigana = true,
                            fontSize = 28.sp,
                            colorByPart = true,
                        )
                        RomajiLine(draft.segments)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemType.entries.forEach { type ->
                        FilterChip(selected = state.type == type, onClick = { viewModel.setType(type) }, label = { Text(type.label()) })
                    }
                }

                if (state.type == ItemType.SENTENCE) {
                    OutlinedTextField(
                        value = state.translation,
                        onValueChange = viewModel::setTranslation,
                        label = { Text("Translation (English)") },
                        supportingText = state.translationError?.let { error -> { Text(error, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = viewModel::translate, enabled = !state.translating) {
                        Text(if (state.translating) "Translating…" else "Translate")
                    }
                } else {
                    OutlinedTextField(
                        value = state.meanings,
                        onValueChange = viewModel::setMeanings,
                        label = { Text("Meanings (one per line)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save card")
            }
        }
    }
}

/** A converted word; tapping it lists the other ways to write it, with a short meaning. */
@Composable
private fun ChunkChip(chunk: Chunk, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectable = chunk.candidates.size > 1 && chunk.choice.pos != PartOfSpeech.PUNCTUATION
    Box {
        AssistChip(
            onClick = { if (selectable) expanded = true },
            label = { Text(chunk.choice.surface, fontSize = 18.sp) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            chunk.candidates.forEachIndexed { index, candidate ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(candidate.surface, fontSize = 18.sp)
                            val level = if (candidate.jlpt in 1..5) "N${candidate.jlpt} · " else ""
                            candidate.meanings.firstOrNull()?.let {
                                Text(level + it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}
