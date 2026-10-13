package io.github.otoniel2004.mangareaderjp.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otoniel2004.mangareaderjp.AppContainer
import io.github.otoniel2004.mangareaderjp.cards.CardDraft
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.data.Segment
import io.github.otoniel2004.mangareaderjp.japanese.Chunk
import io.github.otoniel2004.mangareaderjp.japanese.RomajiToKana
import io.github.otoniel2004.mangareaderjp.srs.Srs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddItemUiState(
    val input: String = "",
    val languageReady: Boolean = false,
    /** Romaji input: the converted pieces, each with its writing options. Empty for Japanese input. */
    val chunks: List<Chunk> = emptyList(),
    /** Japanese input: its words, so the user can make a card from just some of them. */
    val tokens: List<Segment> = emptyList(),
    /** Indexes of tapped words; the card covers the range from the first to the last one. */
    val selectedTokens: Set<Int> = emptySet(),
    val draft: CardDraft? = null,
    val type: ItemType = ItemType.WORD,
    val meanings: String = "",
    val translation: String = "",
    val imagePath: String? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = draft != null && draft.text.isNotBlank() && !saved

    /** Range of words the card is made of, or null for the whole text. */
    val selectedRange: IntRange? get() = if (selectedTokens.isEmpty()) null else selectedTokens.min()..selectedTokens.max()
}

/**
 * New card from typed text:
 *
 *   input (debounced) ──► romaji?  yes → converter → chunks → draft
 *                                   no  → Kuromoji + JMdict → draft
 *   draft → preview, suggested type and meanings (the user can edit both) → save
 */
@OptIn(FlowPreview::class)
class AddItemViewModel(
    private val container: AppContainer,
    initialText: String,
    imagePath: String?,
    private val source: String,
) : ViewModel() {
    private val state = MutableStateFlow(AddItemUiState(input = initialText, imagePath = imagePath))
    val uiState: StateFlow<AddItemUiState> = state.asStateFlow()

    private val input = MutableStateFlow(initialText)
    private var typeEdited = false
    private var meaningsEdited = false

    init {
        viewModelScope.launch {
            combine(input.debounce(250), container.languageReady) { text, ready -> text to ready }
                .collect { (text, ready) ->
                    state.update { it.copy(languageReady = ready) }
                    if (ready) analyze(text)
                }
        }
    }

    fun setInput(text: String) {
        state.update { it.copy(input = text) }
        input.value = text
    }

    fun selectCandidate(chunkIndex: Int, candidateIndex: Int) {
        val chunks = state.value.chunks.toMutableList()
        chunks[chunkIndex] = chunks[chunkIndex].copy(selected = candidateIndex)
        viewModelScope.launch {
            val draft = withContext(Dispatchers.Default) { container.drafts.fromChunks(chunks) }
            applyDraft(chunks, draft)
        }
    }

    /** Tap a word: the card becomes just the selected words; the whole text is kept as context. */
    fun toggleToken(index: Int) {
        val current = state.value
        val selection = if (index in current.selectedTokens) current.selectedTokens - index else current.selectedTokens + index
        state.update { it.copy(selectedTokens = selection) }
        typeEdited = false
        meaningsEdited = false
        viewModelScope.launch {
            val text = textFor(current.tokens, selection) ?: current.input
            val draft = withContext(Dispatchers.Default) { container.drafts.fromJapanese(text) }
            applyDraft(emptyList(), draft)
        }
    }

    private fun textFor(tokens: List<Segment>, selection: Set<Int>): String? =
        if (selection.isEmpty()) null else tokens.subList(selection.min(), selection.max() + 1).joinToString("") { it.surface }

    fun setType(type: ItemType) {
        typeEdited = true
        state.update { it.copy(type = type) }
    }

    fun setMeanings(text: String) {
        meaningsEdited = true
        state.update { it.copy(meanings = text) }
    }

    fun setTranslation(text: String) = state.update { it.copy(translation = text) }

    fun save() {
        val current = state.value
        val draft = current.draft ?: return
        if (!current.canSave) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            container.items.insert(
                SavedItem(
                    type = current.type,
                    text = draft.text,
                    segments = draft.segments,
                    contextSegments = if (current.selectedRange != null) current.tokens else emptyList(),
                    meanings = current.meanings.lines().map(String::trim).filter(String::isNotEmpty),
                    translation = current.translation.trim().ifEmpty { null },
                    source = source,
                    imagePath = current.imagePath,
                    createdAt = now,
                    srs = Srs.newCard(now),
                ),
            )
            state.update { it.copy(saved = true) }
        }
    }

    private suspend fun analyze(text: String) {
        if (text.isBlank()) {
            applyDraft(emptyList(), null)
            return
        }
        val romaji = RomajiToKana.looksLikeRomaji(text)
        val (chunks, tokens, draft) = withContext(Dispatchers.Default) { analyzeOffMain(text, romaji) }
        state.update { it.copy(tokens = tokens, selectedTokens = emptySet()) }
        applyDraft(chunks, draft)
    }

    /** Romaji → chunks with kanji options; Japanese → words the user can pick from. */
    private fun analyzeOffMain(text: String, romaji: Boolean): Triple<List<Chunk>, List<Segment>, CardDraft> {
        if (romaji) {
            val chunks = container.converter.convert(text)
            return Triple(chunks, emptyList(), container.drafts.fromChunks(chunks))
        }
        return Triple(emptyList(), container.analyzer.segments(text), container.drafts.fromJapanese(text))
    }

    private fun applyDraft(chunks: List<Chunk>, draft: CardDraft?) {
        state.update { s ->
            s.copy(
                chunks = chunks,
                draft = draft,
                type = if (typeEdited || draft == null) s.type else draft.type,
                meanings = if (meaningsEdited || draft == null) s.meanings else draft.meanings.joinToString("\n"),
            )
        }
    }
}
