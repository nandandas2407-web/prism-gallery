package com.prism.gallery.ui.theme

/** Built-in liquid-glass looks. Each one is a complete, editable set of theme values. */
object Liquid {
    val presets: List<NamedPreset> = listOf(
        NamedPreset("Prism", ThemeSettings()),
        NamedPreset(
            "Aurora",
            ThemeSettings(
                mode = ThemeMode.DARK, style = GlassStyle.CRYSTAL,
                accent = 0xFF1DE9B6.toInt(), accent2 = 0xFF7C4DFF.toInt(), accent3 = 0xFF2979FF.toInt(),
                blur = .85f, glassOpacity = .28f, orbIntensity = .8f, borderGlow = .8f, shine = .75f,
            ),
        ),
        NamedPreset(
            "Neon Night",
            ThemeSettings(
                mode = ThemeMode.AMOLED, style = GlassStyle.NEON,
                accent = 0xFFFF4081.toInt(), accent2 = 0xFF00E5FF.toInt(), accent3 = 0xFFAA00FF.toInt(),
                blur = .5f, glassOpacity = .22f, cornerRadius = 20f, orbIntensity = .7f, borderGlow = 1f, shine = .3f,
            ),
        ),
        NamedPreset(
            "Sunset",
            ThemeSettings(
                style = GlassStyle.FROSTED,
                accent = 0xFFFF7043.toInt(), accent2 = 0xFFFFB300.toInt(), accent3 = 0xFFEC407A.toInt(),
                blur = .7f, glassOpacity = .35f, orbIntensity = .75f,
            ),
        ),
        NamedPreset(
            "Ice",
            ThemeSettings(
                mode = ThemeMode.LIGHT, style = GlassStyle.CRYSTAL,
                accent = 0xFF2979FF.toInt(), accent2 = 0xFF00BCD4.toInt(), accent3 = 0xFFB388FF.toInt(),
                blur = .6f, glassOpacity = .45f, orbIntensity = .55f, borderGlow = .6f, shine = .8f,
            ),
        ),
        NamedPreset(
            "Mono Ink",
            ThemeSettings(
                mode = ThemeMode.AMOLED, style = GlassStyle.MINIMAL,
                accent = 0xFFB0BEC5.toInt(), accent2 = 0xFF607D8B.toInt(), accent3 = 0xFF90A4AE.toInt(),
                blur = .4f, glassOpacity = .3f, cornerRadius = 12f, orbIntensity = .25f, borderGlow = .3f, shine = 0f,
            ),
        ),
    )

    /** Apply a look but keep the user's layout and accessibility choices. */
    fun apply(look: ThemeSettings, current: ThemeSettings): ThemeSettings = look.copy(
        gridColumns = current.gridColumns,
        reduceMotion = current.reduceMotion,
        highContrast = current.highContrast,
        dynamicColor = current.dynamicColor,
    ).sanitized()
}
