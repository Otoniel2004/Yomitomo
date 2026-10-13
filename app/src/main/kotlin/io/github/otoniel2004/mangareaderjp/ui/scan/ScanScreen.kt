package io.github.otoniel2004.mangareaderjp.ui.scan

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.ui.appViewModel
import java.io.File

/**
 * Card from a photo. Opens the camera (or the gallery) right away, then:
 *
 *   photo with a box around each text found → tap boxes (or the list) to select
 *   "Create card" → "New card" screen with the selected text and a crop of the photo
 *
 * The camera is the phone's own camera app, so this app needs no camera permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    fromCamera: Boolean,
    onBack: () -> Unit,
    onCreateCard: (text: String, imagePath: String?) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = appViewModel { ScanViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var launched by rememberSaveable { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) viewModel.load(context.contentResolver, uri) else if (state.bitmap == null) onBack()
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.load(context.contentResolver, uri) else if (state.bitmap == null) onBack()
    }

    fun openCamera() {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the latest photo is needed
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "photo-${System.currentTimeMillis()}.jpg"))
        photoUri = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    fun openGallery() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            if (fromCamera) openCamera() else openGallery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fromCamera) "Scan with camera" else "Read an image") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Reading the text…")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            state.bitmap?.let { bitmap ->
                Text("Tap the text you want on the card", style = MaterialTheme.typography.labelLarge)
                PhotoWithBoxes(state, onTap = viewModel::toggle, aspect = bitmap.width.toFloat() / bitmap.height)

                state.blocks.forEachIndexed { index, block ->
                    val position = state.selected.indexOf(index)
                    Card(
                        onClick = { viewModel.toggle(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (position >= 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            (if (position >= 0) "${position + 1}. " else "") + block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Button(
                    onClick = { viewModel.createCard(onCreateCard) },
                    enabled = state.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create card from ${state.selected.size} selected") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::openCamera, modifier = Modifier.weight(1f)) { Text("Take a photo") }
                OutlinedButton(onClick = ::openGallery, modifier = Modifier.weight(1f)) { Text("Pick an image") }
            }
        }
    }
}

/** The photo, with a box per text block (filled when selected). Taps select the block under the finger. */
@Composable
private fun PhotoWithBoxes(state: ScanUiState, onTap: (Int) -> Unit, aspect: Float) {
    val bitmap = state.bitmap ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().aspectRatio(aspect)) {
        Image(image, contentDescription = "Photo", modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state.blocks) {
                    detectTapGestures { tap ->
                        val scale = size.width / bitmap.width.toFloat()
                        val x = (tap.x / scale).toInt()
                        val y = (tap.y / scale).toInt()
                        val hit = state.blocks.indexOfFirst { it.box.expandedBy(12).contains(x, y) }
                        if (hit >= 0) onTap(hit)
                    }
                },
        ) {
            val scale = size.width / bitmap.width
            state.blocks.forEachIndexed { index, block ->
                val topLeft = Offset(block.box.left * scale, block.box.top * scale)
                val boxSize = Size(block.box.width * scale, block.box.height * scale)
                if (index in state.selected) drawRect(accent.copy(alpha = 0.25f), topLeft, boxSize)
                drawRect(if (index in state.selected) accent else Color(0xCC00B8D4), topLeft, boxSize, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

