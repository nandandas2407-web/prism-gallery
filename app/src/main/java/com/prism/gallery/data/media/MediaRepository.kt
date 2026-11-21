package com.prism.gallery.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for device media (MediaStore). */
@Singleton
class MediaRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val _items = MutableStateFlow<List<GalleryMedia>>(emptyList())
    val items: StateFlow<List<GalleryMedia>> = _items

    suspend fun refresh() {
        _items.value = withContext(Dispatchers.IO) {
            (query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false) + query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true))
                .sortedByDescending { it.dateTaken }
        }
    }

    fun byId(id: Long) = _items.value.firstOrNull { it.id == id }

    private fun query(base: Uri, video: Boolean): List<GalleryMedia> {
        val proj = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            if (video) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN,
            if (video) MediaStore.Video.Media.DURATION else MediaStore.MediaColumns._ID,
        )
        val out = mutableListOf<GalleryMedia>()
        ctx.contentResolver.query(base, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val taken = c.getLong(8).takeIf { it > 0 } ?: (c.getLong(3) * 1000)
                out += GalleryMedia(
                    id = id,
                    uriString = ContentUris.withAppendedId(base, id).toString(),
                    name = c.getString(1) ?: "",
                    mime = c.getString(2) ?: "",
                    dateTaken = taken,
                    size = c.getLong(4),
                    width = c.getInt(5),
                    height = c.getInt(6),
                    durationMs = if (video) c.getLong(9) else 0L,
                    bucket = c.getString(7) ?: "Unknown",
                )
            }
        }
        return out
    }
}
