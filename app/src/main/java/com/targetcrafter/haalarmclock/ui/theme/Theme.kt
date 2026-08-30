package com.targetcrafter.haalarmclock.ui.theme

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

private val AmberPrimary = Color(0xFFFFB84D)
private val NightBackground = Color(0xFF1B1F3B)

private val DarkColors = darkColorScheme(primary = AmberPrimary, background = NightBackground)
private val LightColors = lightColorScheme(primary = Color(0xFF8A5A00))

// Material 3 Expressive's own theme wrapper (MaterialExpressiveTheme, MotionScheme.expressive())
// isn't public API yet as of material3 1.4.0 stable -- it's internal there, only promoted to
// public in 1.5.0-alpha19+. Staying on stable material3 for now, so this is plain MaterialTheme;
// swap to MaterialExpressiveTheme once that lands in a stable release.
@Composable
fun HaAlarmClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
