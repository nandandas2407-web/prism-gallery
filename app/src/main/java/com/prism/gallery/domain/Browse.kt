package com.prism.gallery.domain

import com.prism.gallery.data.media.GalleryMedia
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Grouping(val pattern: String?) {
    DAY("EEE, d MMM yyyy"), MONTH("MMMM yyyy"), YEAR("yyyy"), NONE(null)
}

enum class SortOrder { NEWEST, OLDEST, NAME, SIZE }

fun List<GalleryMedia>.sortedBy(order: SortOrder): List<GalleryMedia> = when (order) {
    SortOrder.NEWEST -> sortedByDescending { it.dateTaken }
    SortOrder.OLDEST -> sortedBy { it.dateTaken }
    SortOrder.NAME -> sortedBy { it.name.lowercase() }
    SortOrder.SIZE -> sortedByDescending { it.size }
}

fun groupLabel(item: GalleryMedia, g: Grouping, zone: ZoneId = ZoneId.systemDefault()): String =
    g.pattern?.let { DateTimeFormatter.ofPattern(it, Locale.getDefault()).format(Instant.ofEpochMilli(item.dateTaken).atZone(zone)) } ?: ""

enum class MediaKind { PHOTO, VIDEO, GIF, RAW }

val rawMimes = setOf("image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/x-sony-arw", "image/dng")

fun GalleryMedia.kind(): MediaKind = when {
    isVideo -> MediaKind.VIDEO
    mime == "image/gif" -> MediaKind.GIF
    mime in rawMimes || name.substringAfterLast('.', "").lowercase() in setOf("dng", "cr2", "nef", "arw") -> MediaKind.RAW
    else -> MediaKind.PHOTO
}

data class SearchFilters(
    val query: String = "",
    val fromMs: Long? = null,
    val toMs: Long? = null,
    val kind: MediaKind? = null,
    val minMegapixels: Float = 0f,
    val minSizeBytes: Long = 0,
)

/** [index] holds on-device ML text (OCR) and labels per media id. */
fun List<GalleryMedia>.filter(f: SearchFilters, index: Map<Long, String> = emptyMap()): List<GalleryMedia> {
    val q = f.query.trim().lowercase()
    return filter { m ->
        (f.fromMs == null || m.dateTaken >= f.fromMs) &&
            (f.toMs == null || m.dateTaken <= f.toMs) &&
            (f.kind == null || m.kind() == f.kind) &&
            m.pixels >= f.minMegapixels * 1_000_000 &&
            m.size >= f.minSizeBytes &&
            (q.isEmpty() || m.name.lowercase().contains(q) || m.bucket.lowercase().contains(q) || index[m.id]?.lowercase()?.contains(q) == true)
    }
}
