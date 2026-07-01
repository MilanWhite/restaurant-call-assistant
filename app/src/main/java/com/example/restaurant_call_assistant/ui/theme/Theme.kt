package com.example.restaurant_call_assistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = AppBlack,
    onPrimary = AppWhite,
    secondary = AppDarkBeige,
    onSecondary = AppWhite,
    tertiary = AppBeige,
    onTertiary = AppBlack,
    background = AppWhite,
    onBackground = AppBlack,
    surface = AppWhite,
    onSurface = AppBlack,
    surfaceVariant = AppLine,
    onSurfaceVariant = AppMuted,
    outline = AppBlack
)

@Composable
fun RestaurantcallassistantTheme(
    @Suppress("UNUSED_PARAMETER")
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER")
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
