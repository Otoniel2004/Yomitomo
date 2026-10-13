package io.github.otoniel2004.mangareaderjp.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.otoniel2004.mangareaderjp.reader.ScreenCapture
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
    var showOverlayDialog by remember { mutableStateOf(false) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenReaderService.start(context, result.resultCode, data)
        }
    }

    fun requestCapture() = captureLauncher.launch(ScreenCapture.consentIntent(context))

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
    var showText by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (reader.running) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (reader.running) "Reader on" else "Reader off",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            !reader.running -> "Reads the screen looking for Japanese while you use another app."
                            reader.captureBlocked -> "The app on screen blocks screen capture."
                            reader.pagesDetected == 0 -> "Open your manga app and turn pages."
                            else -> "Page ${reader.pagesDetected} · ${reader.lastPageText.size} text blocks " +
                                "· OCR ${reader.lastOcrMillis} ms"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = reader.running, onCheckedChange = onToggle)
            }
            if (reader.running && reader.lastPageText.isNotEmpty()) {
                TextButton(onClick = { showText = true }) { Text("View text from last page") }
            }
        }
    }

    if (showText) {
        AlertDialog(
            onDismissRequest = { showText = false },
            title = { Text("Text on the last page") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    reader.lastPageText.forEachIndexed { index, block ->
                        Text("${index + 1}. $block", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showText = false }) { Text("Close") } },
        )
    }
}
