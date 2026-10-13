package io.github.otoniel2004.mangareaderjp.ui.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otoniel2004.mangareaderjp.AppContainer
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.data.ReaderSettings
import io.github.otoniel2004.mangareaderjp.data.SecretStore
import io.github.otoniel2004.mangareaderjp.translation.DeepLClient
import io.github.otoniel2004.mangareaderjp.translation.TranslationException
import io.github.otoniel2004.mangareaderjp.ui.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** What the DeepL section shows. The key itself never leaves [SecretStore] for display. */
data class DeepLUiState(
    val hasKey: Boolean = false,
    val isFreePlan: Boolean = false,
    val checking: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settings = container.settings
    val reader: StateFlow<ReaderSettings> =
        settings.readerSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    private val deepLState = MutableStateFlow(DeepLUiState())
    val deepL: StateFlow<DeepLUiState> = deepLState.asStateFlow()

    init {
        viewModelScope.launch { refreshKeyState(message = null) }
    }

    fun setShowFuriganaOnPage(value: Boolean) = launch { settings.setShowFuriganaOnPage(value) }
    fun setShowStatusDot(value: Boolean) = launch { settings.setShowStatusDot(value) }
    fun setKeepScreenOn(value: Boolean) = launch { settings.setKeepScreenOn(value) }
    fun setShowOcrBoxes(value: Boolean) = launch { settings.setShowOcrBoxes(value) }
    fun setAutoTranslate(value: Boolean) = launch { settings.setAutoTranslate(value) }

    /** Checks the key with DeepL first; only a key DeepL accepts is stored. */
    fun saveKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        deepLState.value = deepLState.value.copy(checking = true, message = null, error = false)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeepLClient { trimmed }.usage() } }
            result.onSuccess { usage ->
                withContext(Dispatchers.IO) { container.secrets.put(SecretStore.DEEPL_API_KEY, trimmed) }
                refreshKeyState("Key saved. ${usageText(usage.used, usage.limit)}")
            }.onFailure { e ->
                deepLState.value = deepLState.value.copy(checking = false, message = e.userMessage(), error = true)
            }
        }
    }

    fun checkUsage() {
        deepLState.value = deepLState.value.copy(checking = true, message = null, error = false)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { container.deepL.usage() } }
            deepLState.value = deepLState.value.copy(
                checking = false,
                message = result.fold({ usageText(it.used, it.limit) }, { it.userMessage() }),
                error = result.isFailure,
            )
        }
    }

    fun removeKey() = launch {
        withContext(Dispatchers.IO) { container.secrets.remove(SecretStore.DEEPL_API_KEY) }
        refreshKeyState("Key removed from this phone.")
    }

    private suspend fun refreshKeyState(message: String?) {
        val key = withContext(Dispatchers.IO) { container.secrets.get(SecretStore.DEEPL_API_KEY) }
        deepLState.value = DeepLUiState(hasKey = key != null, isFreePlan = key?.endsWith(":fx") == true, message = message)
    }

    private fun usageText(used: Long, limit: Long): String {
        val format = NumberFormat.getIntegerInstance(Locale.US)
        return "Used ${format.format(used)} of ${format.format(limit)} characters this month."
    }

    private fun Throwable.userMessage() = (this as? TranslationException)?.message ?: "Could not reach DeepL."

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOptionsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it) }
    val state by viewModel.reader.collectAsStateWithLifecycle()
    val deepL by viewModel.deepL.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Translation (DeepL)")
            DeepLSection(deepL, viewModel)
            OptionRow(
                title = "Translate pages automatically",
                description = "Every new page is translated right away, so two taps show the English " +
                    "instantly. Off: only the sentences you open are translated (saves quota).",
                checked = state.autoTranslate,
                onChange = viewModel::setAutoTranslate,
            )
            HorizontalDivider()

            SectionTitle("Reader")
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
                "Reader changes apply the next time you turn the reader on.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun DeepLSection(state: DeepLUiState, viewModel: SettingsViewModel) {
    var keyInput by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hasKey) {
            Text(
                "API key saved on this phone (${if (state.isFreePlan) "Free plan" else "Pro plan"}).",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::checkUsage, enabled = !state.checking) { Text("Check usage") }
                OutlinedButton(onClick = viewModel::removeKey, enabled = !state.checking) { Text("Remove key") }
            }
        } else {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("DeepL API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    viewModel.saveKey(keyInput)
                    keyInput = ""
                },
                enabled = keyInput.isNotBlank() && !state.checking,
            ) { Text(if (state.checking) "Checking…" else "Check and save") }
        }
        state.message?.let {
            Text(
                it,
                color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "The key is stored encrypted, only on this phone. It is sent only to DeepL.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp),
    )
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
