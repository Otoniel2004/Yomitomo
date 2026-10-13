package io.github.otoniel2004.mangareaderjp.ui.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otoniel2004.mangareaderjp.AppContainer
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box
import io.github.otoniel2004.mangareaderjp.reader.ocr.JapaneseOcr
import io.github.otoniel2004.mangareaderjp.reader.ocr.OcrDebug
import io.github.otoniel2004.mangareaderjp.reader.ocr.OcrBlock
import io.github.otoniel2004.mangareaderjp.reader.ocr.OcrFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

data class ScanUiState(
    val bitmap: Bitmap? = null,
    val blocks: List<OcrBlock> = emptyList(),
    /** Indexes of selected blocks, in the order they were tapped (= order of the card text). */
    val selected: List<Int> = emptyList(),
    val working: Boolean = false,
    val error: String? = null,
)

/**
 * Photo → card:
 *
 *   photo/gallery image → decode (upright, max 2048 px) → OCR + filter → blocks
 *   user taps blocks → their text (in tap order) + a crop of that area → "New card" screen
 */
class ScanViewModel(private val container: AppContainer) : ViewModel() {
    private val state = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = state.asStateFlow()

    private var ocr: JapaneseOcr? = null

    fun load(resolver: ContentResolver, uri: Uri) {
        state.value = ScanUiState(working = true)
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeUpright(resolver, uri) }
                val engine = ocr ?: JapaneseOcr().also { ocr = it }
                val blocks = withContext(Dispatchers.Default) {
                    val raw = engine.recognize(bitmap)
                    OcrFilter.clean(raw).also { OcrDebug.record(container.context, "image", bitmap, raw, it) }
                }
                state.value = ScanUiState(
                    bitmap = bitmap,
                    blocks = blocks,
                    selected = if (blocks.size == 1) listOf(0) else emptyList(),
                    error = if (blocks.isEmpty()) "No Japanese text found. Try a closer, sharper photo." else null,
                )
            } catch (e: Exception) {
                state.value = ScanUiState(error = "Could not read this image (${e.message}).")
            }
        }
    }

    fun toggle(index: Int) = state.update { s ->
        s.copy(selected = if (index in s.selected) s.selected - index else s.selected + index)
    }

    /** Saves a crop of the selected area and hands back the text and the crop's path. */
    fun createCard(onReady: (text: String, imagePath: String?) -> Unit) {
        val s = state.value
        val bitmap = s.bitmap ?: return
        val chosen = s.selected.map { s.blocks[it] }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val area = chosen.map { it.box }.reduce(Box::union).expandedBy(CROP_MARGIN)
                saveCrop(bitmap, area)
            }
            onReady(chosen.joinToString("") { it.text }, path)
        }
    }

    private fun saveCrop(bitmap: Bitmap, area: Box): String? {
        val left = area.left.coerceIn(0, bitmap.width - 1)
        val top = area.top.coerceIn(0, bitmap.height - 1)
        val right = area.right.coerceIn(left + 1, bitmap.width)
        val bottom = area.bottom.coerceIn(top + 1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val file = File(container.items.imagesDir(), "scan-${System.currentTimeMillis()}.jpg")
        return runCatching {
            file.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.path
        }.getOrNull()
    }

    override fun onCleared() {
        ocr?.close()
    }

    private companion object {
        const val MAX_SIZE = 2048
        const val CROP_MARGIN = 16

        /** Decodes the image the right way up (phone photos store their rotation separately, in EXIF). */
        fun decodeUpright(resolver: ContentResolver, uri: Uri): Bitmap {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // ImageDecoder applies the EXIF rotation itself.
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val largest = max(info.size.width, info.size.height)
                    if (largest > MAX_SIZE) decoder.setTargetSampleSize(ceil(largest / MAX_SIZE.toDouble()).toInt())
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val largest = max(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                inSampleSize = if (largest > MAX_SIZE) ceil(largest / MAX_SIZE.toDouble()).toInt() else 1
            }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("unsupported image")
            val degrees = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (degrees == 0f) return bitmap
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        }
    }
}
