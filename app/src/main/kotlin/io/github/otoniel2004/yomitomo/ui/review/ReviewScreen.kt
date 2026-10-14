package io.github.otoniel2004.yomitomo.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otoniel2004.yomitomo.R
import io.github.otoniel2004.yomitomo.data.SavedItem
import io.github.otoniel2004.yomitomo.data.SavedItemRepository
import io.github.otoniel2004.yomitomo.srs.Rating
import io.github.otoniel2004.yomitomo.ui.appViewModel
import io.github.otoniel2004.yomitomo.ui.components.Flashcard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val loading: Boolean = true,
    val queue: List<SavedItem> = emptyList(),
    val reviewed: Int = 0,
) {
    val current: SavedItem? get() = queue.firstOrNull()
}

/**
 * Review session: loads every due card and shows them one at a time.
 * "Again" sends the card to the end of the queue, like Anki, so it comes back in this session.
 */
class ReviewViewModel(private val items: SavedItemRepository) : ViewModel() {
    private val state = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = ReviewUiState(loading = false, queue = items.dueItems(System.currentTimeMillis()))
        }
    }

    fun rate(rating: Rating) {
        val current = state.value.current ?: return
        viewModelScope.launch {
            val updated = items.review(current, rating, System.currentTimeMillis())
            state.update { s ->
                val rest = s.queue.drop(1)
                s.copy(
                    queue = if (rating == Rating.AGAIN) rest + updated else rest,
                    reviewed = s.reviewed + 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { ReviewViewModel(it.items) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val current = state.current
            when {
                state.loading -> LinearProgressIndicator()
                current == null -> {
                    Text("All done! 🎉", style = MaterialTheme.typography.headlineSmall)
                    Text("${state.reviewed} reviews this session.")
                    Button(onClick = onBack) { Text("Back to home") }
                }
                else -> {
                    Text("${state.queue.size} left · ${state.reviewed} done")
                    Flashcard(current, onRate = viewModel::rate)
                }
            }
        }
    }
}
