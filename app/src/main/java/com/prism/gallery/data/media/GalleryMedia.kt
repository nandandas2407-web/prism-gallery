package com.prism.gallery.data.media

import android.net.Uri

data class GalleryMedia(
    val id: Long,
    val uriString: String,
    val name: String,
    val mime: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucket: String,
) {
    val uri: Uri get() = Uri.parse(uriString)
    val isVideo: Boolean get() = mime.startsWith("video/")
    val pixels: Long get() = width.toLong() * height
}
