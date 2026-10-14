package io.github.otoniel2004.mangareaderjp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.ui.appViewModel
import io.github.otoniel2004.mangareaderjp.ui.components.FuriganaText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (Long) -> Unit,
    onReview: () -> Unit,
    onOpenOptions: () -> Unit,
    onOpenStats: () -> Unit,
    onAddTyped: () -> Unit,
    onScan: (fromCamera: Boolean) -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.items) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manga Reader JP") },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(painterResource(R.drawable.ic_chart), contentDescription = "Statistics")
                    }
                    IconButton(onClick = onOpenOptions) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = { AddButton(onAddTyped, onScan) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ReaderControl(state.reader) }
            item { ReviewBanner(dueCount = state.dueIds.size, onReview = onReview) }
            item { FilterRow(state.filter, viewModel::setFilter) }

            if (!state.loading && state.items.isEmpty()) {
                item {
                    Text(
                        "Nothing saved yet. Once the reader is active, tap \"Save\" on a translation and it shows up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.items, key = { it.id }) { item ->
                SavedItemCard(item, isDue = item.id in state.dueIds, onClick = { onOpenItem(item.id) })
            }
        }
    }
}

@Composable
private fun ReviewBanner(dueCount: Int, onReview: () -> Unit) {
    FilledTonalButton(
        onClick = onReview,
        enabled = dueCount > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (dueCount > 0) "Review now ($dueCount)" else "No reviews due")
    }
}

@Composable
private fun FilterRow(selected: ItemType?, onSelect: (ItemType?) -> Unit) {
    val options = listOf(null to "All") + ItemType.entries.map { it to it.label() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (type, label) ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SavedItemCard(item: SavedItem, isDue: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.type.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (isDue) Badge { Text("due") }
            }
            FuriganaText(
                segments = item.segments,
                showFurigana = item.showFurigana,
                colorByPart = true,
            )
            Text(
                item.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun ItemType.label(): String = when (this) {
    ItemType.WORD -> "Word"
    ItemType.EXPRESSION -> "Expression"
    ItemType.SENTENCE -> "Sentence"
}

/** "+ Add" button: type a card yourself, or read one from a photo. */
@Composable
private fun AddButton(onAddTyped: () -> Unit, onScan: (fromCamera: Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            onClick = { expanded = true },
            icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Add") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Type (romaji or Japanese)") }, onClick = { expanded = false; onAddTyped() })
            DropdownMenuItem(text = { Text("Scan with camera") }, onClick = { expanded = false; onScan(true) })
            DropdownMenuItem(text = { Text("Read an image from the gallery") }, onClick = { expanded = false; onScan(false) })
        }
    }
}
