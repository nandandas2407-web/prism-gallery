package com.prism.gallery.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.gallery.data.SettingsRepository
import com.prism.gallery.data.sync.SyncScheduler
import com.prism.gallery.ui.components.GlassSurface
import com.prism.gallery.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(val repo: SettingsRepository) : ViewModel() {
    val theme = repo.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettings())
    val presets = repo.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun update(f: (ThemeSettings) -> ThemeSettings) = viewModelScope.launch { repo.update(f) }
    fun savePreset(n: String) = viewModelScope.launch { repo.savePreset(n, theme.value) }
    fun deletePreset(n: String) = viewModelScope.launch { repo.deletePreset(n) }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val t by vm.theme.collectAsState()
    val presets by vm.presets.collectAsState()
    var presetName by remember { mutableStateOf("") }
    var wifiOnly by remember { mutableStateOf(true) }
    var charging by remember { mutableStateOf(false) }
    var syncOn by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        u?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(t.export().toByteArray()) } }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { ctx.contentResolver.openInputStream(it)?.use { i -> ThemeSettings.import(i.readBytes().decodeToString()) }?.let { n -> vm.update { n } } }
    }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()).padding(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Card("Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(t.mode == m, { vm.update { it.copy(mode = m) } }, { Text(m.name.lowercase()) }) }
            }
            ColorPick("Accent colour", t.accent) { c -> vm.update { it.copy(accent = c) } }
            if (Build.VERSION.SDK_INT >= 31) Toggle("Material You dynamic colour", t.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
            Slide("Blur intensity", t.blur, 0f..1f) { v -> vm.update { it.copy(blur = v) } }
            Slide("Glass opacity", t.glassOpacity, .05f..0.9f) { v -> vm.update { it.copy(glassOpacity = v) } }
            Slide("Corner radius", t.cornerRadius, 0f..48f) { v -> vm.update { it.copy(cornerRadius = v) } }
            Slide("Grid columns: ${t.gridColumns}", t.gridColumns.toFloat(), 2f..6f, steps = 3) { v -> vm.update { it.copy(gridColumns = v.toInt()) } }
        }
        Card("Liquid glass") {
            Text("Style", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassStyle.entries.forEach { st -> FilterChip(t.style == st, { vm.update { it.copy(style = st) } }, { Text(st.label) }) }
            }
            Text("Looks", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Liquid.presets.forEach { p -> AssistChip({ vm.update { cur -> Liquid.apply(p.settings, cur) } }, { Text(p.name) }) }
            }
            ColorPick("Glow colour 2", t.accent2) { c -> vm.update { it.copy(accent2 = c) } }
            ColorPick("Glow colour 3", t.accent3) { c -> vm.update { it.copy(accent3 = c) } }
            Slide("Orb glow", t.orbIntensity, 0f..1f) { v -> vm.update { it.copy(orbIntensity = v) } }
            Slide("Border glow", t.borderGlow, 0f..1f) { v -> vm.update { it.copy(borderGlow = v) } }
            Slide("Shine", t.shine, 0f..1f) { v -> vm.update { it.copy(shine = v) } }
            Toggle("Animated backdrop", t.animatedBackdrop) { v -> vm.update { it.copy(animatedBackdrop = v) } }
        }
        Card("Presets") {
            OutlinedTextField(presetName, { presetName = it }, label = { Text("Preset name") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ if (presetName.isNotBlank()) { vm.savePreset(presetName); presetName = "" } }) { Text("Save") }
                OutlinedButton({ exporter.launch("prism-theme.json") }) { Text("Export") }
                OutlinedButton({ importer.launch(arrayOf("application/json")) }) { Text("Import") }
            }
            presets.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ vm.update { p.settings } }, Modifier.weight(1f)) { Text(p.name) }
                    TextButton({ vm.deletePreset(p.name) }) { Text("Delete") }
                }
            }
        }
        Card("Accessibility") {
            Toggle("Reduce motion", t.reduceMotion) { v -> vm.update { it.copy(reduceMotion = v) } }
            Toggle("High contrast", t.highContrast) { v -> vm.update { it.copy(highContrast = v) } }
            Text("Font size follows system settings. Colour-blind-safe accents: CB Blue / Orange / Vermillion.", style = MaterialTheme.typography.bodySmall)
        }
        Card("Cloud sync") {
            Toggle("Background sync (WebDAV)", syncOn) { syncOn = it; if (it) SyncScheduler.schedule(ctx, wifiOnly, charging) else SyncScheduler.cancel(ctx) }
            Toggle("Wi-Fi only", wifiOnly) { wifiOnly = it; if (syncOn) SyncScheduler.schedule(ctx, wifiOnly, charging) }
            Toggle("Only while charging", charging) { charging = it; if (syncOn) SyncScheduler.schedule(ctx, wifiOnly, charging) }
        }
        Card("Privacy & language") {
            var disguised by remember { mutableStateOf(false) }
            Toggle("Disguise app as Calculator", disguised) { disguised = it; setDisguise(ctx, it) }
            if (Build.VERSION.SDK_INT >= 33) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("en", "bn", "hi", "es", "de").forEach { tag -> AssistChip({ ctx.getSystemService(android.app.LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag) }, { Text(tag) }) }
            }
        }
    }
}

private fun setDisguise(ctx: Context, on: Boolean) {
    val pm = ctx.packageManager
    fun set(name: String, enabled: Boolean) = pm.setComponentEnabledSetting(
        ComponentName(ctx.packageName, "com.prism.gallery.$name"),
        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
    set("DisguiseAlias", on); set("DefaultAlias", !on)
}

@Composable private fun ColorPick(label: String, argb: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Palettes.presets.forEach { p ->
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color(p.argb))
                    .border(if (p.argb == argb) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .semantics { contentDescription = "$label ${p.name}" }
                    .clickable { onPick(p.argb) },
            )
        }
    }
    val hsv = remember(argb) { FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) } }
    Slider(hsv[0], { onPick(android.graphics.Color.HSVToColor(floatArrayOf(it, .75f, .95f))) }, valueRange = 0f..360f)
    var hex by remember(argb) { mutableStateOf("%06X".format(argb and 0xFFFFFF)) }
    OutlinedTextField(
        hex,
        { v ->
            hex = v.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
            if (hex.length == 6) hex.toIntOrNull(16)?.let { onPick(0xFF000000.toInt() or it) }
        },
        label = { Text("Hex") }, prefix = { Text("#") }, singleLine = true,
    )
}

@Composable private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) = GlassSurface(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); content() }
}
@Composable private fun Toggle(label: String, v: Boolean, on: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f)); Switch(v, on)
}
@Composable private fun Slide(label: String, v: Float, r: ClosedFloatingPointRange<Float>, steps: Int = 0, on: (Float) -> Unit) { Text(label, style = MaterialTheme.typography.labelMedium); Slider(v, on, valueRange = r, steps = steps) }
