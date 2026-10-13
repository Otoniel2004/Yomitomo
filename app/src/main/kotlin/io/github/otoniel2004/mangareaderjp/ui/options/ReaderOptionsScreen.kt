package io.github.otoniel2004.mangareaderjp.ui.options

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.data.ReaderSettings
import io.github.otoniel2004.mangareaderjp.data.SettingsRepository
import io.github.otoniel2004.mangareaderjp.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderOptionsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<ReaderSettings> =
        settings.readerSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    fun setShowFuriganaOnPage(value: Boolean) {
        viewModelScope.launch { settings.setShowFuriganaOnPage(value) }
    }

    fun setShowStatusDot(value: Boolean) {
        viewModelScope.launch { settings.setShowStatusDot(value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { settings.setKeepScreenOn(value) }
    }

    fun setShowOcrBoxes(value: Boolean) {
        viewModelScope.launch { settings.setShowOcrBoxes(value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOptionsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { ReaderOptionsViewModel(it.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reader options") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OptionRow(
                title = "Furigana on page",
                description = "Shows the hiragana reading over kanji and katakana while you read.",
                checked = state.showFuriganaOnPage,
                onChange = viewModel::setShowFuriganaOnPage,
            )
            HorizontalDivider()
            OptionRow(
                title = "Status indicator",
                description = "Corner dot: orange = processing, green = ready, red = error.",
                checked = state.showStatusDot,
                onChange = viewModel::setShowStatusDot,
            )
            HorizontalDivider()
            OptionRow(
                title = "Keep screen on",
                description = "Android stops screen capture when the screen turns off, which would pause " +
                    "the reader. This keeps the screen awake while the reader is on.",
                checked = state.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
            HorizontalDivider()
            OptionRow(
                title = "Show OCR boxes (debug)",
                description = "Draws a box around every piece of Japanese text the reader found.",
                checked = state.showOcrBoxes,
                onChange = viewModel::setShowOcrBoxes,
            )
            HorizontalDivider()
            Text(
                "Changes apply the next time you turn the reader on.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun OptionRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
