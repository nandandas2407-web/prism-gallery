package com.prism.gallery

import com.prism.gallery.data.media.GalleryMedia
import com.prism.gallery.domain.MediaKind
import com.prism.gallery.domain.SearchFilters
import com.prism.gallery.domain.filter
import com.prism.gallery.domain.kind
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFilterTest {
    private fun m(id: Long, name: String, mime: String = "image/jpeg", w: Int = 4000, h: Int = 3000, t: Long = 1000) =
        GalleryMedia(id, "content://x/$id", name, mime, t, 2_000_000, w, h, 0, "Camera")

    private val all = listOf(m(1, "beach.jpg"), m(2, "clip.mp4", "video/mp4", 1920, 1080), m(3, "IMG.dng", "image/x-adobe-dng"), m(4, "small.png", "image/png", 100, 100))

    @Test fun filtersByKind() = assertEquals(listOf(2L), all.filter(SearchFilters(kind = MediaKind.VIDEO)).map { it.id })
    @Test fun detectsRaw() = assertEquals(MediaKind.RAW, all[2].kind())
    @Test fun filtersByResolution() = assertEquals(setOf(1L, 3L), all.filter(SearchFilters(minMegapixels = 10f)).map { it.id }.toSet())
    @Test fun searchesOcrIndex() = assertEquals(listOf(4L), all.filter(SearchFilters(query = "invoice"), mapOf(4L to "Invoice #42")).map { it.id })
}
