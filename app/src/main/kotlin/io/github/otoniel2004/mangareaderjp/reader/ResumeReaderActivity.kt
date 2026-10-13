package io.github.otoniel2004.mangareaderjp.reader

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible screen opened by the "Reader paused — tap to resume" notification.
 * It only shows the system screen-sharing dialog, restarts the reader and closes,
 * so you land right back in your manga app.
 */
class ResumeReaderActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenReaderService.start(this, result.resultCode, data)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consent.launch(ScreenCapture.consentIntent(this))
    }
}
