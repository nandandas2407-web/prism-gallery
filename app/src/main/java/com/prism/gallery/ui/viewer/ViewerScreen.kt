package com.prism.gallery.ui.viewer

import android.app.WallpaperManager
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.prism.gallery.data.db.Favorite
import com.prism.gallery.data.db.GalleryDao
import com.prism.gallery.data.media.GalleryMedia
import com.prism.gallery.data.media.MediaRepository
import com.prism.gallery.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ViewerViewModel @Inject constructor(repo: MediaRepository, private val dao: GalleryDao) : ViewModel() {
    val items = repo.items
    val favorites = dao.favorites().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    fun toggleFavorite(id: Long) = viewModelScope.launch { if (id in favorites.value) dao.removeFavorite(id) else dao.addFavorite(Favorite(id)) }
}

@Composable
fun ViewerScreen(startId: Long, onClose: () -> Unit, onPlay: (Long) -> Unit, onEdit: (Long) -> Unit, vm: ViewerViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    val favs by vm.favorites.collectAsState()
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val pager = rememberPagerState(items.indexOfFirst { it.id == startId }.coerceAtLeast(0)) { items.size }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val current = items[pager.currentPage]
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { onClose() }

    LaunchedEffect(slideshow) {
        while (slideshow) { delay(3000); pager.animateScrollToPage((pager.currentPage + 1) % items.size) }
    }

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            // Swipe down to close (only when not zoomed).
            .pointerInput(zoomed) {
                if (!zoomed) detectVerticalDragGestures(
                    onDragEnd = { scope.launch { if (dragY.value > 300f) onClose() else dragY.animateTo(0f, spring()) } },
                    onDragCancel = { scope.launch { dragY.animateTo(0f, spring()) } },
                ) { _, d -> if (d > 0 || dragY.value > 0) scope.launch { dragY.snapTo((dragY.value + d).coerceAtLeast(0f)) } }
            },
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { items[it].id }) { page ->
            val m = items[page]
            if (m.isVideo) {
                Box(Modifier.fillMaxSize().hapticClickable("Play video ${m.name}") { onPlay(m.id) }, Alignment.Center) {
                    AsyncImage(m.uri, null, Modifier.fillMaxSize().sharedImage("img-${m.id}"), contentScale = ContentScale.Fit)
                    Icon(Icons.Outlined.PlayCircle, null, Modifier.size(72.dp), tint = androidx.compose.ui.graphics.Color.White)
                }
            } else {
                ZoomableImage(m, onZoom = { zoomed = it })
            }
        }
        GlassSurface(Modifier.align(Alignment.BottomCenter).padding(16.dp), RoundedCornerShape(28.dp)) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                val fav = current.id in favs
                Icon(if (fav) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (fav) "Remove favorite" else "Add favorite", Modifier.hapticClickable("Toggle favorite") { vm.toggleFavorite(current.id) })
                Icon(Icons.Outlined.Share, "Share", Modifier.hapticClickable("Share") {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(current.mime).putExtra(Intent.EXTRA_STREAM, current.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                })
                if (!current.isVideo) Icon(Icons.Outlined.Edit, "Edit", Modifier.hapticClickable("Edit") { onEdit(current.id) })
                Icon(Icons.Outlined.Info, "Details", Modifier.hapticClickable("Details") { showInfo = true })
                Icon(Icons.Outlined.Slideshow, "Slideshow", Modifier.hapticClickable("Toggle slideshow") { slideshow = !slideshow })
                if (!current.isVideo) Icon(Icons.Outlined.Wallpaper, "Set as wallpaper", Modifier.hapticClickable("Set as wallpaper") {
                    ctx.contentResolver.openInputStream(current.uri)?.use { WallpaperManager.getInstance(ctx).setStream(it) }
                })
                Icon(Icons.Outlined.Delete, "Move to trash", Modifier.hapticClickable("Move to trash") {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        val pi = MediaStore.createTrashRequest(ctx.contentResolver, listOf(current.uri), true)
                        trash.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                })
            }
        }
    }
    if (showInfo) InfoSheet(current) { showInfo = false }
}

@Composable
private fun ZoomableImage(m: GalleryMedia, onZoom: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    LaunchedEffect(scale > 1.01f) { onZoom(scale > 1.01f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
            // canPan only while zoomed, so one-finger swipes still reach the pager.
            .transformable(state, canPan = { scale > 1.01f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    ) {
        AsyncImage(m.uri, m.name, Modifier.fillMaxSize().sharedImage("img-${m.id}").semantics { contentDescription = "Photo ${m.name}" }, contentScale = ContentScale.Fit)
    }
}

@Composable
private fun InfoSheet(m: GalleryMedia, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val rows = remember(m.id) {
        buildList {
            add("Name" to m.name); add("Type" to m.mime); add("Size" to "${m.size / 1024} KB"); add("Resolution" to "${m.width} × ${m.height}"); add("Album" to m.bucket)
            if (!m.isVideo) runCatching {
                ctx.contentResolver.openInputStream(m.uri)?.use { s ->
                    val e = ExifInterface(s)
                    listOf("Camera" to listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" "),
                        "Lens" to e.getAttribute(ExifInterface.TAG_LENS_MODEL), "Aperture" to e.getAttribute(ExifInterface.TAG_F_NUMBER),
                        "Shutter" to e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME), "ISO" to e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                        "Focal length" to e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH), "Taken" to e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                        "GPS" to e.latLong?.let { "%.5f, %.5f".format(it[0], it[1]) }).forEach { (k, v) -> if (!v.isNullOrBlank()) add(k to v) }
                }
            }
        }
    }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            rows.forEach { (k, v) -> Row { Text(k, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v) } }
        }
    }
}
