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
import io.github.otoniel2004.mangareaderjp.data.FuriganaSize
import io.github.otoniel2004.mangareaderjp.data.ReadingMode
import io.github.otoniel2004.mangareaderjp.data.ReaderSettings
import io.github.otoniel2004.mangareaderjp.reader.GestureForwarderService
import android.content.Intent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDate
import android.provider.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalContext
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

    private val backupState = MutableStateFlow<String?>(null)
    /** Result of the last export/import, shown under the Backup buttons. */
    val backupMessage: StateFlow<String?> = backupState.asStateFlow()

    fun exportCards(resolver: ContentResolver, uri: Uri) = launch {
        backupState.value = runCatching {
            val count = withContext(Dispatchers.IO) {
                container.backup.export { json ->
                    resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("could not open the file")
                }
            }
            "Exported $count cards."
        }.getOrElse { "Export failed: ${it.message}" }
    }

    fun importCards(resolver: ContentResolver, uri: Uri) = launch {
        backupState.value = runCatching {
            val added = withContext(Dispatchers.IO) {
                val text = resolver.openInputStream(uri)?.use(::readLimited)
                    ?: error("could not open the file")
                container.backup.import(text)
            }
            if (added == 0) "Nothing new to import (those cards are already here)." else "Imported $added cards."
        }.getOrElse { "Import failed: ${it.message}" }
    }

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
    fun setFuriganaSize(value: FuriganaSize) = launch { settings.setFuriganaSize(value) }
    fun setBatterySaver(value: Boolean) = launch { settings.setBatterySaver(value) }
    fun setHideKnownFurigana(value: Boolean) = launch { settings.setHideKnownFurigana(value) }
    fun setReadingMode(value: ReadingMode) = launch { settings.setReadingMode(value) }

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
    val swipesEnabled by GestureForwarderService.isConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportCards(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCards(context.contentResolver, uri)
    }
    // Re-checked whenever the screen comes back (e.g. from the battery settings).
    var unrestrictedBattery by remember { mutableStateOf(isUnrestricted(context)) }
    LifecycleResumeEffect(Unit) {
        unrestrictedBattery = isUnrestricted(context)
        onPauseOrDispose { }
    }

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

            SectionTitle("Backup")
            BackupSection(
                message = backupMessage,
                onExport = { exportLauncher.launch("manga-reader-cards-${LocalDate.now()}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
            HorizontalDivider()

            SectionTitle("Reader")
            ReadingModeRow(state.readingMode, viewModel::setReadingMode)
            HorizontalDivider()
            BatteryRow(unrestrictedBattery) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            HorizontalDivider()
            OptionRow(
                title = "Furigana on page",
                description = "Shows the hiragana reading over kanji and katakana while you read.",
                checked = state.showFuriganaOnPage,
                onChange = viewModel::setShowFuriganaOnPage,
            )
            HorizontalDivider()
            FuriganaSizeRow(state.furiganaSize, viewModel::setFuriganaSize)
            HorizontalDivider()
            OptionRow(
                title = "Hide furigana for words you know",
                description = "No reading over words whose cards you remember well (reviewed up to 21+ days " +
                    "apart). The furigana fades away as you learn.",
                checked = state.hideKnownFurigana,
                onChange = viewModel::setHideKnownFurigana,
            )
            HorizontalDivider()
            SwipeRow(swipesEnabled) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            HorizontalDivider()
            OptionRow(
                title = "Battery saver",
                description = "Checks the screen half as often. A new page takes a moment longer to be read.",
                checked = state.batterySaver,
                onChange = viewModel::setBatterySaver,
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

/** Manga: two taps = the whole bubble. Book: two taps = the sentence under your finger. */
@Composable
private fun ReadingModeRow(selected: ReadingMode, onSelect: (ReadingMode) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reading mode", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == ReadingMode.MANGA, onClick = { onSelect(ReadingMode.MANGA) }, label = { Text("Manga") })
            FilterChip(selected = selected == ReadingMode.BOOK, onClick = { onSelect(ReadingMode.BOOK) }, label = { Text("Book") })
        }
        Text(
            if (selected == ReadingMode.MANGA) "Two taps translate the whole speech bubble."
            else "Two taps translate only the sentence under your finger (novels have long paragraphs).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FuriganaSizeRow(selected: FuriganaSize, onSelect: (FuriganaSize) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Furigana size", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FuriganaSize.entries.forEach { size ->
                FilterChip(
                    selected = size == selected,
                    onClick = { onSelect(size) },
                    label = { Text(size.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
    }
}

/** Swipes that start on a bubble need the optional accessibility service; only the user can turn it on. */
@Composable
private fun SwipeRow(enabled: Boolean, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Swipe on text turns the page", style = MaterialTheme.typography.titleMedium)
        Text(
            if (enabled) "On. A swipe that starts on a text bubble is passed to your manga app."
            else "Off. Swipes that start on a text bubble don't turn the page. To fix it, open " +
                "Accessibility → Installed apps → \"Manga Reader JP – page swipes\" and turn it on. " +
                "It only replays your swipe; it can't read the screen.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!enabled) OutlinedButton(onClick = onOpenSettings) { Text("Open Accessibility settings") }
    }
}

@Composable
private fun BackupSection(message: String?, onExport: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Your cards live only on this phone. Export them to a file (keep it in Drive or on your " +
                "computer) and import it on any phone. Images and study progress included.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport) { Text("Export cards") }
            OutlinedButton(onClick = onImport) { Text("Import cards") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Samsung (One UI) and other phones may stop background apps; "Unrestricted" keeps the reader alive. */
@Composable
private fun BatteryRow(unrestricted: Boolean, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Battery: unrestricted", style = MaterialTheme.typography.titleMedium)
        Text(
            if (unrestricted) "On. Android won't stop the reader in the background."
            else "Off. Android may stop the reader while you read. Open the app settings → Battery → " +
                "Unrestricted.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!unrestricted) OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}

private fun isUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

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

/** Backups are a few MB; refuse anything absurdly large instead of running out of memory. */
private const val MAX_BACKUP_BYTES = 200L * 1024 * 1024

private fun readLimited(input: java.io.InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= MAX_BACKUP_BYTES) { "the file is too large to be a backup" }
        out.write(buffer, 0, read)
    }
    return out.toString(Charsets.UTF_8.name())
}
