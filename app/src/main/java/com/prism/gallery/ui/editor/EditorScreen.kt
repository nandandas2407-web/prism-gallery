package com.prism.gallery.ui.editor

import android.content.ContentValues
import android.graphics.*
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.prism.gallery.data.db.EditRecord
import com.prism.gallery.data.db.GalleryDao
import com.prism.gallery.data.media.MediaRepository
import com.prism.gallery.ui.components.GlassSurface
import com.prism.gallery.ui.components.hapticClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.pow

/** Non-destructive edit description. exposure/warmth double as basic RAW-style controls on the decoded preview. */
@Serializable
data class EditParams(
    val rotation: Int = 0,
    val exposureEv: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val filter: String = "None",
) {
    fun colorMatrix(): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(saturation) }
        val e = 2f.pow(exposureEv)
        val c = contrast
        val t = (1f - c) * 128f
        m.postConcat(ColorMatrix(floatArrayOf(
            c * e * (1 + warmth * .2f), 0f, 0f, 0f, t,
            0f, c * e, 0f, 0f, t,
            0f, 0f, c * e * (1 - warmth * .2f), 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )))
        when (filter) {
            "Mono" -> m.postConcat(ColorMatrix().apply { setSaturation(0f) })
            "Sepia" -> m.postConcat(ColorMatrix(floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            "Vivid" -> m.postConcat(ColorMatrix().apply { setSaturation(1.5f) })
            "Cool" -> m.postConcat(ColorMatrix(floatArrayOf(.9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.15f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        }
        return m
    }
}

@HiltViewModel
class EditorViewModel @Inject constructor(val repo: MediaRepository, val dao: GalleryDao) : ViewModel()

@Composable
fun EditorScreen(id: Long, onClose: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val media = vm.repo.byId(id) ?: return
    var p by remember { mutableStateOf(EditParams()) }
    LaunchedEffect(id) { vm.dao.edit(id)?.let { p = Json.decodeFromString(it.paramsJson) } }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AsyncImage(
            media.uri, null, Modifier.weight(1f).fillMaxWidth().graphicsLayer { rotationZ = p.rotation.toFloat() },
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(p.colorMatrix().array)),
        )
        GlassSurface(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()).heightIn(max = 280.dp)) {
                Slide("Exposure (EV)", p.exposureEv, -2f..2f) { p = p.copy(exposureEv = it) }
                Slide("Warmth", p.warmth, -1f..1f) { p = p.copy(warmth = it) }
                Slide("Contrast", p.contrast, 0.5f..1.5f) { p = p.copy(contrast = it) }
                Slide("Saturation", p.saturation, 0f..2f) { p = p.copy(saturation = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("None", "Mono", "Sepia", "Vivid", "Cool").forEach { f ->
                        FilterChip(p.filter == f, { p = p.copy(filter = f) }, { Text(f) })
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Rotate90DegreesCcw, "Rotate", Modifier.hapticClickable("Rotate 90 degrees") { p = p.copy(rotation = (p.rotation - 90) % 360) })
                    TextButton({ p = EditParams() }) { Text("Reset") }
                    TextButton({ scope.launch { vm.dao.upsertEdit(EditRecord(id, Json.encodeToString(p))); onClose() } }) { Text("Save edit") }
                    Button({ scope.launch { export(ctx, media.uriString, p); onClose() } }) { Text("Export copy") }
                }
            }
        }
    }
}

@Composable
private fun Slide(label: String, v: Float, r: ClosedFloatingPointRange<Float>, on: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(v, on, valueRange = r)
}


/** Renders the edit to a new JPEG; the original stays untouched. */
private suspend fun export(ctx: android.content.Context, uriString: String, p: EditParams) = withContext(Dispatchers.IO) {
    val uri = android.net.Uri.parse(uriString)
    val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext
    val matrix = Matrix().apply { postRotate(p.rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(rotated, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(p.colorMatrix()) })
    val cv = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Prism_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrismGallery")
    }
    ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { dst ->
        ctx.contentResolver.openOutputStream(dst)?.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }
}
