package com.prism.gallery.ui.photos

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.prism.gallery.domain.Grouping
import com.prism.gallery.domain.SortOrder
import com.prism.gallery.ui.components.*
import com.prism.gallery.ui.theme.LocalThemeSettings
import kotlin.math.roundToInt

@Composable
fun PhotosScreen(
    bucket: String? = null,
    favoritesOnly: Boolean = false,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: PhotosViewModel = hiltViewModel(),
) {
    val theme = LocalThemeSettings.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val rows by vm.rows.collectAsState()
    val selected by vm.selected.collectAsState()
    val grouping by vm.grouping.collectAsState()
    val sort by vm.sort.collectAsState()
    LaunchedEffect(bucket, favoritesOnly) { vm.configure(bucket, favoritesOnly) }

    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.clearSelection() }

    PermissionGate(onGranted = { vm.refresh() }) {
        val state = rememberLazyGridState()
        var columns by remember(theme.gridColumns) { mutableIntStateOf(theme.gridColumns) }
        var menu by remember { mutableStateOf(false) }
        // Phones use the chosen density; tablets scale it up with the available width.
        val widthDp = LocalConfiguration.current.screenWidthDp
        val effectiveColumns = (columns * (widthDp / 400f).coerceIn(1f, 3f)).roundToInt().coerceAtLeast(2)

        fun hit(o: Offset): Long? = state.layoutInfo.visibleItemsInfo
            .firstOrNull { IntRect(it.offset, it.size).contains(IntOffset(o.x.roundToInt(), o.y.roundToInt())) }?.key as? Long

        Column(Modifier.fillMaxSize()) {
            GlassSurface(Modifier.fillMaxWidth().padding(12.dp), RoundedCornerShape(28.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selected.isEmpty()) (bucket ?: if (favoritesOnly) "Favorites" else "Photos") else "${selected.size} selected",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                    )
                    if (selected.isEmpty()) {
                        Box {
                            Icon(Icons.Outlined.Sort, "Sort and group", Modifier.hapticClickable("Sort and group") { menu = true })
                            DropdownMenu(menu, { menu = false }) {
                                Grouping.entries.forEach { g -> DropdownMenuItem({ Text("Group: ${g.name.lowercase()}") }, { vm.grouping.value = g; menu = false }) }
                                SortOrder.entries.forEach { s -> DropdownMenuItem({ Text("Sort: ${s.name.lowercase()}") }, { vm.sort.value = s; menu = false }) }
                            }
                        }
                        Icon(Icons.Outlined.Settings, "Settings", Modifier.hapticClickable("Settings", onSettings))
                    } else {
                        Icon(Icons.Outlined.FavoriteBorder, "Favorite selected", Modifier.hapticClickable("Favorite selected") { vm.favoriteSelected() })
                        Icon(Icons.Outlined.Share, "Share selected", Modifier.hapticClickable("Share selected") {
                            val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(vm.uris(selected)))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(Intent.createChooser(i, null))
                        })
                        Icon(Icons.Outlined.Delete, "Move to trash", Modifier.hapticClickable("Move to trash") {
                            if (Build.VERSION.SDK_INT >= 30) {
                                // System trash keeps items for 30 days before permanent deletion.
                                val pi = MediaStore.createTrashRequest(ctx.contentResolver, vm.uris(selected), true)
                                trashLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                            } else {
                                Toast.makeText(ctx, "Trash needs Android 11+", Toast.LENGTH_SHORT).show()
                            }
                        })
                        Icon(Icons.Outlined.Close, "Clear selection", Modifier.hapticClickable("Clear selection") { vm.clearSelection() })
                    }
                }
            }
            LazyVerticalGrid(
                state = state,
                columns = GridCells.Fixed(effectiveColumns),
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // Pinch to change density without consuming scroll events.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var acc = 1f
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.changes.size >= 2) {
                                    acc *= e.calculateZoom()
                                    if (acc > 1.4f) { columns = (columns - 1).coerceAtLeast(2); acc = 1f; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                    if (acc < .7f) { columns = (columns + 1).coerceAtMost(6); acc = 1f; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                }
                            } while (e.changes.any { it.pressed })
                        }
                    }
                    // Long-press then drag across cells to multi-select.
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { o -> hit(o)?.let { vm.add(it); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } },
                            onDrag = { c, _ -> hit(c.position)?.let(vm::add) },
                            onDragEnd = {}, onDragCancel = {},
                        )
                    },
            ) {
                items(rows, key = { r -> if (r is GridRow.Cell) r.item.id else (r as GridRow.Header).title }, span = { r ->
                    if (r is GridRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                }) { r ->
                    when (r) {
                        is GridRow.Header -> Text(r.title, Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall)
                        is GridRow.Cell -> Thumb(r, r.item.id in selected, selected.isNotEmpty(), theme.reduceMotion, { onOpen(r.item.id) }, { vm.toggle(r.item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(cell: GridRow.Cell, selected: Boolean, selecting: Boolean, reduceMotion: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val m = cell.item
    val scale by animateFloatAsState(
        if (selected) .88f else 1f,
        if (reduceMotion) spring(stiffness = Spring.StiffnessHigh) else spring(Spring.DampingRatioMediumBouncy),
        label = "sel",
    )
    Box(
        Modifier.aspectRatio(1f).scale(scale).clip(RoundedCornerShape(10.dp))
            .semantics { contentDescription = (if (m.isVideo) "Video " else "Photo ") + m.name + if (selected) ", selected" else "" }
            .combinedClickable(onClick = { if (selecting) onToggle() else onOpen() }, onLongClick = onToggle),
    ) {
        AsyncImage(m.uri, null, Modifier.fillMaxSize().sharedImage("img-${m.id}"), contentScale = ContentScale.Crop)
        if (m.isVideo) Icon(Icons.Outlined.PlayCircle, null, Modifier.align(Alignment.BottomStart).padding(4.dp), tint = androidx.compose.ui.graphics.Color.White)
        if (selected) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(4.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PermissionGate(onGranted: () -> Unit, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val request = if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun granted(): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> (has(Manifest.permission.READ_MEDIA_IMAGES) && has(Manifest.permission.READ_MEDIA_VIDEO)) ||
            (Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) // partial access
        else -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var ok by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = granted() }
    LaunchedEffect(ok) { if (ok) onGranted() }
    if (ok) content() else Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Welcome to Prism Gallery", style = MaterialTheme.typography.headlineSmall)
                Text("Allow access to photos and videos so your library can appear here. Photos stay on your device.")
                Button({ launcher.launch(request) }) { Text("Allow access") }
            }
        }
    }
}
