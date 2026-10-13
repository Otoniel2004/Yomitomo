package io.github.otoniel2004.mangareaderjp.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.otoniel2004.mangareaderjp.reader.ReaderStatus
import io.github.otoniel2004.mangareaderjp.reader.ScreenReaderService

/**
 * The reader on/off card. Turning it on walks through Android's permissions:
 *
 *   1. "Display over other apps"   (first time only, opens system settings)
 *   2. Notifications               (Android 13+, first time only)
 *   3. "Share your screen" dialog  (every time — Android rule)
 *   4. starts ScreenReaderService
 */
@Composable
fun ReaderControl(reader: ReaderStatus.State) {
    val context = LocalContext.current
    val projectionManager = remember { context.getSystemService(MediaProjectionManager::class.java) }
    var showOverlayDialog by remember { mutableStateOf(false) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenReaderService.start(context, result.resultCode, data)
        }
    }

    fun requestCapture() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: ask for the entire screen, not a single app.
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        captureLauncher.launch(intent)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { requestCapture() } // capture works even if notifications are denied

    fun continueWithoutOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestCapture()
        }
    }

    fun enable() {
        if (!Settings.canDrawOverlays(context)) showOverlayDialog = true else continueWithoutOverlay()
    }

    ReaderCard(
        reader = reader,
        onToggle = { on -> if (on) enable() else ScreenReaderService.stop(context) },
    )

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Allow display over other apps") },
            text = {
                Text(
                    "The reader draws furigana, translations and a small status dot on top of your " +
                        "manga app. On the next screen, find Manga Reader JP and turn it on, then come " +
                        "back and flip the switch again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    continueWithoutOverlay()
                }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun ReaderCard(reader: ReaderStatus.State, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (reader.running) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (reader.running) "Reader on" else "Reader off",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        !reader.running -> "Reads the screen looking for Japanese while you use another app."
                        reader.captureBlocked -> "The app on screen blocks screen capture."
                        else -> "Pages detected: ${reader.pagesDetected}. Open your manga app and turn pages."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = reader.running, onCheckedChange = onToggle)
        }
    }
}
