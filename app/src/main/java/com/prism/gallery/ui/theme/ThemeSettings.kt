package com.prism.gallery.ui.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode { LIGHT, DARK, AMOLED, AUTO }

/** How glass panels are drawn. */
enum class GlassStyle(val label: String) { FROSTED("Frosted"), CRYSTAL("Crystal"), NEON("Neon"), MINIMAL("Minimal") }

data class Preset(val name: String, val argb: Int)

object Palettes {
    val presets = listOf(
        Preset("Prism Violet", 0xFF7C4DFF.toInt()),
        Preset("Aqua", 0xFF00BCD4.toInt()),
        Preset("Sunset", 0xFFFF7043.toInt()),
        Preset("Rose", 0xFFEC407A.toInt()),
        Preset("Lime", 0xFF9CCC65.toInt()),
        Preset("Amber", 0xFFFFB300.toInt()),
        Preset("Ocean", 0xFF2979FF.toInt()),
        Preset("Mint", 0xFF1DE9B6.toInt()),
        // Okabe-Ito colour-blind-safe picks
        Preset("CB Blue", 0xFF0072B2.toInt()),
        Preset("CB Orange", 0xFFE69F00.toInt()),
        Preset("CB Vermillion", 0xFFD55E00.toInt()),
    )
}

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.AUTO,
    val accent: Int = 0xFF7C4DFF.toInt(),
    val dynamicColor: Boolean = false,
    val blur: Float = 0.6f,
    val glassOpacity: Float = 0.35f,
    val cornerRadius: Float = 24f,
    val gridColumns: Int = 3,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    // Liquid-glass customisation
    val style: GlassStyle = GlassStyle.FROSTED,
    val accent2: Int = 0xFF18C8E8.toInt(),
    val accent3: Int = 0xFFFF4081.toInt(),
    val orbIntensity: Float = 0.6f,
    val borderGlow: Float = 0.7f,
    val shine: Float = 0.5f,
    val animatedBackdrop: Boolean = true,
) {
    fun sanitized() = copy(
        blur = blur.coerceIn(0f, 1f),
        glassOpacity = glassOpacity.coerceIn(0.05f, 0.9f),
        cornerRadius = cornerRadius.coerceIn(0f, 48f),
        gridColumns = gridColumns.coerceIn(2, 6),
        orbIntensity = orbIntensity.coerceIn(0f, 1f),
        borderGlow = borderGlow.coerceIn(0f, 1f),
        shine = shine.coerceIn(0f, 1f),
    )

    fun export(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
        fun import(text: String): ThemeSettings? =
            runCatching { json.decodeFromString(serializer(), text).sanitized() }.getOrNull()
    }
}

@Serializable
data class NamedPreset(val name: String, val settings: ThemeSettings)
