package com.prism.gallery

import com.prism.gallery.ui.theme.GlassStyle
import com.prism.gallery.ui.theme.Liquid
import com.prism.gallery.ui.theme.ThemeMode
import com.prism.gallery.ui.theme.ThemeSettings
import org.junit.Assert.*
import org.junit.Test

class ThemeSettingsTest {
    @Test fun exportImportRoundTrip() {
        val s = ThemeSettings(mode = ThemeMode.AMOLED, accent = 0xFF00BCD4.toInt(), gridColumns = 5, blur = .8f)
        assertEquals(s, ThemeSettings.import(s.export()))
    }
    @Test fun importClampsOutOfRangeValues() {
        val s = ThemeSettings(gridColumns = 99, blur = 5f, cornerRadius = -4f).sanitized()
        assertEquals(6, s.gridColumns); assertEquals(1f, s.blur); assertEquals(0f, s.cornerRadius)
    }
    @Test fun invalidJsonReturnsNull() = assertNull(ThemeSettings.import("not json"))
    @Test fun liquidFieldsRoundTrip() {
        val s = ThemeSettings(style = GlassStyle.NEON, accent2 = 0xFF00E5FF.toInt(), shine = .2f, animatedBackdrop = false)
        assertEquals(s, ThemeSettings.import(s.export()))
    }
    @Test fun liquidPresetsAreValidAndKeepLayout() {
        Liquid.presets.forEach { assertEquals(it.settings, it.settings.sanitized()) }
        val applied = Liquid.apply(Liquid.presets[1].settings, ThemeSettings(gridColumns = 5, reduceMotion = true))
        assertEquals(5, applied.gridColumns); assertTrue(applied.reduceMotion)
    }
}
