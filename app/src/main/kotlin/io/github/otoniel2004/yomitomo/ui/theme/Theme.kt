package io.github.otoniel2004.yomitomo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF3A4F8C),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFFC2185B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C4FF),
    secondary = Color(0xFFC4C5DD),
    tertiary = Color(0xFFFF8FB1),
)

/**
 * App theme. On Android 12+ (including the S24 FE) it uses "Material You":
 * colors come from the phone wallpaper. Follows the system light/dark mode.
 */
@Composable
fun YomitomoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
