package com.prism.gallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.prism.gallery.data.SettingsRepository
import com.prism.gallery.ui.nav.AppRoot
import com.prism.gallery.ui.theme.GalleryTheme
import com.prism.gallery.ui.theme.ThemeSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// FragmentActivity (a ComponentActivity) is required by BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by settings.theme.collectAsState(ThemeSettings())
            GalleryTheme(theme) { AppRoot() }
        }
    }
}
