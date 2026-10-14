package io.github.otoniel2004.mangareaderjp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.data.SavedItemRepository
import io.github.otoniel2004.mangareaderjp.reader.ReaderStatus
import io.github.otoniel2004.mangareaderjp.srs.Srs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val loading: Boolean = true,
    val reader: ReaderStatus.State = ReaderStatus.State(),
    val filter: CardFilter = CardFilter(),
    /** Card sources present in the collection ("Reader", "Manual"…), for the source chips. */
    val sources: List<String> = emptyList(),
    val items: List<SavedItem> = emptyList(),
    val totalCount: Int = 0,
    val dueIds: Set<Long> = emptySet(),
)

class HomeViewModel(private val items: SavedItemRepository) : ViewModel() {
    private val filter = MutableStateFlow(CardFilter())

    /** A "clock" that ticks every minute, so cards become due without reopening the screen. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        items.observeAll(), ReaderStatus.state, filter, clock,
    ) { all, reader, currentFilter, now ->
        HomeUiState(
            loading = false,
            reader = reader,
            filter = currentFilter,
            sources = all.mapNotNull { it.source }.distinct().sorted(),
            items = all.filter(currentFilter::matches),
            totalCount = all.size,
            dueIds = all.filter { Srs.isDue(it.srs, now) }.map { it.id }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setType(type: ItemType?) = filter.update { it.copy(type = type) }

    fun setSource(source: String?) = filter.update { it.copy(source = source) }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
}
