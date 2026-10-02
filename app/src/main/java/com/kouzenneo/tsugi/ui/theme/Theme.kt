package com.kouzenneo.tsugi.ui.theme

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

private val Light = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F0D2),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357),
    surfaceVariant = Color(0xFFDCE5DD),
    background = Color(0xFFFBFDF9),
    surface = Color(0xFFFBFDF9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9AD4B6),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF14513A),
    onPrimaryContainer = Color(0xFFB6F0D2),
    secondary = Color(0xFFB5CCBE),
    surfaceVariant = Color(0xFF404943),
    background = Color(0xFF0E1411),
    surface = Color(0xFF0E1411),
)

@Composable
fun TsugiTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
