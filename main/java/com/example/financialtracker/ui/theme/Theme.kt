package com.example.financialtracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Custom colors that match your app's theme
private val AppGreen = Color(0xFF4ADE80)
private val AppDarkBG = Color(0xFF111827)
private val AppSurface = Color(0xFF1F2937)
private val AppError = Color(0xFFF87171)

private val DarkColorScheme = darkColorScheme(
    primary = AppGreen,
    onPrimary = Color.Black,
    secondary = Color.DarkGray,
    background = AppDarkBG,
    surface = AppSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    error = AppError,
    onError = Color.Black
)

// A light theme is not used but is defined for completeness
private val LightColorScheme = lightColorScheme(
    primary = AppGreen,
    onPrimary = Color.Black,
    secondary = Color.DarkGray,
    background = Color(0xFFF9F9F9),
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    error = AppError,
    onError = Color.White
)

@Composable
fun FinancialTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // On Android 12+, use dynamic colors from the user's wallpaper
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicDarkColorScheme(context)
        }
        // On older devices, use our custom dark theme
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}