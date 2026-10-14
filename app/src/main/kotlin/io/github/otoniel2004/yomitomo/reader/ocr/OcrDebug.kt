package io.github.otoniel2004.yomitomo.reader.ocr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.Log
import java.io.File

/**
 * Debug builds only: records what the OCR was given and what it returned, so screen-capture
 * and photo results can be compared. Images go to the app cache (never leave the phone):
 *
 *   adb shell run-as io.github.otoniel2004.yomitomo ls cache/ocr-debug
 *   adb logcat -s OcrDebug
 */
object OcrDebug {
    private const val TAG = "OcrDebug"

    fun record(context: Context, label: String, bitmap: Bitmap, raw: List<OcrBlock>, kept: List<OcrBlock>) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.d(TAG, "[$label] ${bitmap.width}x${bitmap.height} ${bitmap.config} raw=${raw.size} kept=${kept.size}")
        raw.forEach { Log.d(TAG, "[$label] raw  ${it.box} ${it.text.replace('\n', '|')}") }
        kept.forEach { Log.d(TAG, "[$label] kept ${it.box} ${it.text}") }
        runCatching {
            val dir = File(context.cacheDir, "ocr-debug").apply { mkdirs() }
            File(dir, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { Log.w(TAG, "could not save $label.png", it) }
    }
}
