package com.prism.gallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }

@Composable
fun GalleryTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val accent = Color(settings.accent)
    val base = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = accent, secondary = accent.copy(alpha = .8f), tertiary = Color(settings.accent2), background = Color(0xFF0B0B14), surface = Color(0xFF14141F))
        else -> lightColorScheme(primary = accent, secondary = accent.copy(alpha = .8f), tertiary = Color(settings.accent2), background = Color(0xFFF3F1FA), surface = Color(0xFFFFFFFF))
    }
    val scheme = if (settings.mode == ThemeMode.AMOLED) base.copy(background = Color.Black, surface = Color(0xFF050505)) else base
    val r = settings.cornerRadius.dp
    CompositionLocalProvider(LocalThemeSettings provides settings) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(RoundedCornerShape(r / 3), RoundedCornerShape(r / 2), RoundedCornerShape(r), RoundedCornerShape(r), RoundedCornerShape(r)),
            content = content,
        )
    }
}
