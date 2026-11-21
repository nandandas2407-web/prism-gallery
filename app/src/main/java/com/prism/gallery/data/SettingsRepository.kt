package com.prism.gallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prism.gallery.ui.theme.NamedPreset
import com.prism.gallery.ui.theme.ThemeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val Context.appStore by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val presetsKey = stringPreferencesKey("presets")
    private val ser = ListSerializer(NamedPreset.serializer())

    val theme: Flow<ThemeSettings> = ctx.appStore.data.map { it[themeKey]?.let(ThemeSettings::import) ?: ThemeSettings() }
    val presets: Flow<List<NamedPreset>> = ctx.appStore.data.map {
        it[presetsKey]?.let { s -> runCatching { Json.decodeFromString(ser, s) }.getOrNull() } ?: emptyList()
    }

    suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        ctx.appStore.edit {
            val cur = it[themeKey]?.let(ThemeSettings::import) ?: ThemeSettings()
            it[themeKey] = transform(cur).sanitized().export()
        }
    }

    suspend fun savePreset(name: String, s: ThemeSettings) = editPresets { list -> list.filterNot { it.name == name } + NamedPreset(name, s) }
    suspend fun deletePreset(name: String) = editPresets { list -> list.filterNot { it.name == name } }

    private suspend fun editPresets(f: (List<NamedPreset>) -> List<NamedPreset>) {
        ctx.appStore.edit {
            val cur = it[presetsKey]?.let { s -> runCatching { Json.decodeFromString(ser, s) }.getOrNull() } ?: emptyList()
            it[presetsKey] = Json.encodeToString(ser, f(cur))
        }
    }
}
