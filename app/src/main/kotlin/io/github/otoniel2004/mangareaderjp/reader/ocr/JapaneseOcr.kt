package io.github.otoniel2004.mangareaderjp.reader.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.Closeable

/**
 * Japanese OCR with Google ML Kit. Runs fully on the phone (the model is bundled in the APK).
 * [recognize] blocks until done, so call it from a background thread.
 */
class JapaneseOcr : Closeable {
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    fun recognize(bitmap: Bitmap): List<OcrBlock> {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        return result.textBlocks.mapNotNull { block ->
            val box = block.boundingBox?.toBox() ?: return@mapNotNull null
            val lines = block.lines.mapNotNull { line ->
                line.boundingBox?.let { OcrLine(line.text, it.toBox()) }
            }
            OcrBlock(block.text, box, lines)
        }
    }

    override fun close() = recognizer.close()

    private fun Rect.toBox() = Box(left, top, right, bottom)
}
