package io.github.otoniel2004.mangareaderjp.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.data.SavedItemRepository
import io.github.otoniel2004.mangareaderjp.srs.StudyStats
import io.github.otoniel2004.mangareaderjp.ui.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

class StatsViewModel(private val items: SavedItemRepository) : ViewModel() {
    private val state = MutableStateFlow<StudyStats?>(null)
    val stats: StateFlow<StudyStats?> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = withContext(Dispatchers.IO) { items.stats(System.currentTimeMillis()) }
        }
    }
}

/**
 * Study statistics:
 *   streak · reviews today · retention (how often you remembered) · cards known
 *   a small bar chart of the last 7 days
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { StatsViewModel(it.items) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = stats
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Streak", if (current.streakDays == 1) "1 day" else "${current.streakDays} days", Modifier.weight(1f))
                Tile("Reviews today", current.reviewsToday.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(
                    "Remembered (30 days)",
                    current.retention?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    Modifier.weight(1f),
                )
                Tile("Due now", current.dueNow.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Cards", current.totalCards.toString(), Modifier.weight(1f))
                Tile("Known (21+ days)", current.known.toString(), Modifier.weight(1f))
            }
            Text("Last 7 days", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            WeekChart(current.lastSevenDays)
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** One bar per day, today last, labelled with the weekday and the count. */
@Composable
private fun WeekChart(counts: List<Int>) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val today = LocalDate.now()
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEachIndexed { index, count ->
                val day = today.minusDays((counts.size - 1 - index).toLong())
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.75f * count / max + 0.02f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
