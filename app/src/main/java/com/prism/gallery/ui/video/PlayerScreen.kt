package com.prism.gallery.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.*
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.prism.gallery.data.media.MediaRepository
import com.prism.gallery.ui.components.GlassSurface
import com.prism.gallery.ui.components.hapticClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class PlayerViewModel @Inject constructor(val repo: MediaRepository) : ViewModel()

private enum class DragMode { NONE, BRIGHTNESS, VOLUME, SEEK }

@Composable
fun PlayerScreen(id: Long, onClose: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val media = vm.repo.byId(id) ?: return
    val player = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(media.uri)); prepare(); playWhenReady = true } }
    DisposableEffect(Unit) {
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); player.release() }
    }

    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var boosting by remember { mutableStateOf(false) }
    var boostSpeed by remember { mutableFloatStateOf(2f) }
    var locked by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var loopA by remember { mutableStateOf<Long?>(null) }
    var loopB by remember { mutableStateOf<Long?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var hud by remember { mutableStateOf("") }

    // Position ticker + A–B loop.
    LaunchedEffect(Unit) {
        while (true) {
            posMs = player.currentPosition
            val b = loopB
            if (b != null && posMs >= b) player.seekTo(loopA ?: 0L)
            delay(150)
        }
    }
    LaunchedEffect(hud) { if (hud.isNotEmpty()) { delay(900); hud = "" } }

    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val mime = if (uri.toString().endsWith("vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
        val sub = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val pos = player.currentPosition
        player.setMediaItem(MediaItem.Builder().setUri(media.uri).setSubtitleConfigurations(listOf(sub)).build(), pos)
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = false } },
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
        )
        // Gesture layer
        var mode by remember { mutableStateOf(DragMode.NONE) }
        var dragX by remember { mutableFloatStateOf(0f) }
        var dragY by remember { mutableFloatStateOf(0f) }
        var startX by remember { mutableFloatStateOf(0f) }
        if (!locked) Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { o ->
                            val d = if (o.x < size.width / 2) -10_000L else 10_000L
                            player.seekTo((player.currentPosition + d).coerceIn(0, player.duration.coerceAtLeast(0)))
                            hud = if (d < 0) "−10s" else "+10s"
                        },
                        onLongPress = { boosting = true; player.setPlaybackSpeed(boostSpeed); hud = "${boostSpeed}x" },
                        onPress = { tryAwaitRelease(); if (boosting) { boosting = false; player.setPlaybackSpeed(speed) } },
                    )
                }
                .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> zoom = (zoom * z).coerceIn(1f, 4f) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> startX = o.x; dragX = 0f; dragY = 0f; mode = DragMode.NONE },
                        onDragEnd = { if (mode == DragMode.SEEK) player.seekTo((player.currentPosition + (dragX * 60).toLong()).coerceAtLeast(0)); mode = DragMode.NONE },
                    ) { _, d ->
                        dragX += d.x; dragY += d.y
                        if (mode == DragMode.NONE && (abs(dragX) > 24 || abs(dragY) > 24)) {
                            mode = if (abs(dragX) > abs(dragY)) DragMode.SEEK else if (startX < size.width / 2) DragMode.BRIGHTNESS else DragMode.VOLUME
                        }
                        when (mode) {
                            DragMode.BRIGHTNESS -> {
                                val cur = act.window.attributes.screenBrightness.let { if (it < 0) .5f else it }
                                val v = (cur - d.y / size.height).coerceIn(.02f, 1f)
                                act.window.attributes = act.window.attributes.apply { screenBrightness = v }
                                hud = "Brightness ${(v * 100).toInt()}%"
                            }
                            DragMode.VOLUME -> {
                                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val v = (am.getStreamVolume(AudioManager.STREAM_MUSIC) - d.y / size.height * max * 1.5f).toInt().coerceIn(0, max)
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                hud = "Volume ${v * 100 / max}%"
                            }
                            DragMode.SEEK -> hud = "Seek ${(dragX * 60 / 1000).toInt()}s"
                            DragMode.NONE -> {}
                        }
                    }
                },
        )
        if (hud.isNotEmpty()) GlassSurface(Modifier.align(Alignment.Center), RoundedCornerShape(20.dp)) { Text(hud, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }

        if (locked) {
            Icon(Icons.Outlined.LockOpen, "Unlock controls", Modifier.align(Alignment.CenterEnd).padding(16.dp).hapticClickable("Unlock controls") { locked = false }, tint = androidx.compose.ui.graphics.Color.White)
        } else GlassSurface(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(), RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(12.dp)) {
                Slider(
                    posMs.toFloat(), { player.seekTo(it.toLong()) },
                    valueRange = 0f..(player.duration.takeIf { it > 0 } ?: 1L).toFloat(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Close, "Close player", Modifier.hapticClickable("Close player", onClose))
                    Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play", Modifier.hapticClickable(if (playing) "Pause" else "Play") { playing = !playing; if (playing) player.play() else player.pause() })
                    TextButton({ loopA = if (loopA == null) posMs else null; if (loopA == null) loopB = null }) { Text(if (loopA == null) "A" else "A✓") }
                    TextButton({ if (loopA != null) loopB = if (loopB == null) posMs else null }) { Text(if (loopB == null) "B" else "B✓") }
                    Icon(Icons.Outlined.Subtitles, "Add subtitles", Modifier.hapticClickable("Add subtitles") { subPicker.launch(arrayOf("*/*")) })
                    Icon(Icons.Outlined.Audiotrack, "Next audio track", Modifier.hapticClickable("Next audio track") {
                        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                        if (groups.size > 1) {
                            val i = groups.indexOfFirst { it.isSelected }
                            val next = groups[(i + 1) % groups.size]
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
                            hud = "Audio ${(i + 1) % groups.size + 1}/${groups.size}"
                        }
                    })
                    Icon(Icons.Outlined.PictureInPicture, "Picture in picture", Modifier.hapticClickable("Picture in picture") { act.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) })
                    Icon(Icons.Outlined.CameraAlt, "Capture frame", Modifier.hapticClickable("Capture frame") { hud = if (captureFrame(ctx, media.uri, posMs)) "Saved frame" else "Capture failed" })
                    Icon(Icons.Outlined.Lock, "Lock controls", Modifier.hapticClickable("Lock controls") { locked = true })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${"%.2f".format(speed)}x", Modifier.width(56.dp))
                    Slider(speed, { speed = (it * 20).toInt() / 20f; player.setPlaybackSpeed(speed) }, valueRange = 0.25f..5f, modifier = Modifier.weight(1f))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 5f).forEach { s -> FilterChip(speed == s, { speed = s; player.setPlaybackSpeed(s) }, { Text("${s}x") }) }
                    FilterChip(boostSpeed == 4f, { boostSpeed = if (boostSpeed == 4f) 2f else 4f }, { Text("Hold boost ${boostSpeed.toInt()}x") })
                }
            }
        }
    }
}

private fun captureFrame(ctx: Context, uri: Uri, posMs: Long): Boolean = runCatching {
    val r = MediaMetadataRetriever().apply { setDataSource(ctx, uri) }
    val bmp = r.getFrameAtTime(posMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: return false
    r.release()
    val cv = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Frame_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrismGallery")
    }
    val dst = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
    ctx.contentResolver.openOutputStream(dst)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    true
}.getOrDefault(false)
