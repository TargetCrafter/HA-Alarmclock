package com.targetcrafter.haalarmclock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
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

// Material 3 Expressive: bouncier, springier motion for state changes (switches, FAB, sheet
// open/close) than the flatter "standard" motion scheme.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
