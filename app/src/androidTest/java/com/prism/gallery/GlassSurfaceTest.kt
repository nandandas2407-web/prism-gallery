package com.prism.gallery

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.prism.gallery.ui.components.GlassSurface
import com.prism.gallery.ui.theme.GalleryTheme
import com.prism.gallery.ui.theme.ThemeSettings
import org.junit.Rule
import org.junit.Test

class GlassSurfaceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun glassSurfaceRendersContent() {
        rule.setContent { GalleryTheme(ThemeSettings()) { GlassSurface { Text("Prism") } } }
        rule.onNodeWithText("Prism").assertIsDisplayed()
    }
}
