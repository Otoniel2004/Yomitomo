package io.github.otoniel2004.mangareaderjp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.data.SavedItemRepository
import io.github.otoniel2004.mangareaderjp.data.Segment
import io.github.otoniel2004.mangareaderjp.japanese.Romaji
import io.github.otoniel2004.mangareaderjp.srs.Rating
import io.github.otoniel2004.mangareaderjp.srs.Srs
import io.github.otoniel2004.mangareaderjp.ui.appViewModel
import io.github.otoniel2004.mangareaderjp.ui.components.Flashcard
import io.github.otoniel2004.mangareaderjp.ui.components.FuriganaText
import io.github.otoniel2004.mangareaderjp.ui.components.RomajiLine
import io.github.otoniel2004.mangareaderjp.ui.components.label
import io.github.otoniel2004.mangareaderjp.ui.components.particleColor
import io.github.otoniel2004.mangareaderjp.ui.home.label
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemDetailViewModel(private val id: Long, private val items: SavedItemRepository) : ViewModel() {
    val item: StateFlow<SavedItem?> =
        items.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setShowFurigana(show: Boolean) {
        viewModelScope.launch { items.setShowFurigana(id, show) }
    }

    fun rate(rating: Rating) {
        val current = item.value ?: return
        viewModelScope.launch { items.review(current, rating, System.currentTimeMillis()) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            items.delete(id)
            onDone()
        }
    }
}

/**
 * Screen of a saved item:
 *   1. the big text with romaji and the furigana toggle
 *   2. meanings / translation
 *   3. (sentences) word-by-word breakdown with highlighted particles
 *   4. the context where it was found
 *   5. study (Anki-style flashcard) + stats
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(id: Long, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "item-$id") { ItemDetailViewModel(id, it.items) }
    val item by viewModel.item.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.type?.label() ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        val current = item ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(current, viewModel::setShowFurigana)

            if (current.type == ItemType.SENTENCE) {
                Section("Sentence breakdown") { SentenceBreakdown(current.segments, current.showFurigana) }
            }

            if (current.contextSegments.isNotEmpty()) {
                Section("Found in") {
                    FuriganaText(current.contextSegments, current.showFurigana, fontSize = 20.sp)
                    RomajiLine(current.contextSegments)
                    current.contextTranslation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Section("Study") {
                Flashcard(current, onRate = viewModel::rate)
                StudyStats(current)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?") },
            text = { Text("It will be removed from home and from your review history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HeaderCard(item: SavedItem, onToggleFurigana: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FuriganaText(
                segments = item.segments,
                showFurigana = item.showFurigana,
                fontSize = if (item.type == ItemType.SENTENCE) 26.sp else 40.sp,
                highlightParticles = item.type == ItemType.SENTENCE,
            )
            RomajiLine(item.segments)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show furigana", Modifier.weight(1f))
                Switch(checked = item.showFurigana, onCheckedChange = onToggleFurigana)
            }
            HorizontalDivider()
            if (item.translation != null) {
                Text(item.translation, style = MaterialTheme.typography.titleMedium)
            }
            item.meanings.forEachIndexed { index, meaning ->
                Text("${index + 1}. $meaning", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Lists each piece of the sentence with its romaji and part of speech; particles highlighted. */
@Composable
private fun SentenceBreakdown(segments: List<Segment>, showFurigana: Boolean) {
    segments.filter { it.pos != PartOfSpeech.PUNCTUATION }.forEach { segment ->
        val isParticle = segment.pos == PartOfSpeech.PARTICLE
        Row(verticalAlignment = Alignment.Bottom) {
            FuriganaText(listOf(segment), showFurigana, fontSize = 20.sp, highlightParticles = true)
            Text("  ")
            Text(
                Romaji.fromSegments(listOf(segment)) + " · ",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
            )
            Text(
                segment.pos.label(),
                style = MaterialTheme.typography.labelLarge,
                color = if (isParticle) particleColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyStats(item: SavedItem) {
    val now = System.currentTimeMillis()
    val next = if (Srs.isDue(item.srs, now)) "now"
    else {
        val minutes = (item.srs.dueAt - now) / Srs.MINUTE
        if (minutes < 60 * 24) "in ${minutes.coerceAtLeast(1)} min"
        else "in ${Srs.formatDays((minutes / (60 * 24)).toInt())}"
    }
    Row {
        Stat("Next review", next, Modifier.weight(1f))
        Stat("Streak", item.srs.repetitions.toString(), Modifier.weight(1f))
        Stat("Lapses", item.srs.lapses.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
