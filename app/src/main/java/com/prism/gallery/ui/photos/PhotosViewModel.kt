package com.prism.gallery.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.gallery.data.db.Favorite
import com.prism.gallery.data.db.GalleryDao
import com.prism.gallery.data.media.GalleryMedia
import com.prism.gallery.data.media.MediaRepository
import com.prism.gallery.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GridRow {
    data class Header(val title: String) : GridRow
    data class Cell(val item: GalleryMedia) : GridRow
}

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val repo: MediaRepository,
    private val dao: GalleryDao,
) : ViewModel() {
    private val bucket = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    val grouping = MutableStateFlow(Grouping.DAY)
    val sort = MutableStateFlow(SortOrder.NEWEST)
    val selected = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = dao.favorites().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val rows: StateFlow<List<GridRow>> = combine(repo.items, bucket, favoritesOnly, favorites, grouping, sort) { arr ->
        @Suppress("UNCHECKED_CAST")
        val items = arr[0] as List<GalleryMedia>
        val b = arr[1] as String?
        val favOnly = arr[2] as Boolean
        val favs = arr[3] as Set<Long>
        val g = arr[4] as Grouping
        val s = arr[5] as SortOrder
        val list = items.filter { (b == null || it.bucket == b) && (!favOnly || it.id in favs) }.sortedBy(s)
        buildList {
            var last: String? = null
            list.forEach { m ->
                if (g != Grouping.NONE && s in setOf(SortOrder.NEWEST, SortOrder.OLDEST)) {
                    val label = groupLabel(m, g)
                    if (label != last) { add(GridRow.Header(label)); last = label }
                }
                add(GridRow.Cell(m))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun configure(bucket: String?, favs: Boolean) { this.bucket.value = bucket; favoritesOnly.value = favs }
    fun refresh() = viewModelScope.launch { repo.refresh() }
    fun toggle(id: Long) { selected.update { if (id in it) it - id else it + id } }
    fun add(id: Long) { selected.update { it + id } }
    fun clearSelection() { selected.value = emptySet() }
    fun favoriteSelected() = viewModelScope.launch {
        selected.value.forEach { dao.addFavorite(Favorite(it)) }
        clearSelection()
    }
    fun uris(ids: Set<Long>) = ids.mapNotNull { repo.byId(it)?.uri }
}
