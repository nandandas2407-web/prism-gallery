package com.prism.gallery.ui.search

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.prism.gallery.data.db.GalleryDao
import com.prism.gallery.data.db.SavedSearch
import com.prism.gallery.data.media.GalleryMedia
import com.prism.gallery.data.media.MediaRepository
import com.prism.gallery.domain.MediaKind
import com.prism.gallery.domain.SearchFilters
import com.prism.gallery.domain.filter
import com.prism.gallery.ui.components.GlassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** On-device ML: OCR text + image labels (people/objects). Nothing leaves the phone. */
suspend fun describe(ctx: Context, m: GalleryMedia): String = withContext(Dispatchers.Default) {
    runCatching {
        val img = InputImage.fromFilePath(ctx, m.uri)
        val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img).await().text
        val labels = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).process(img).await().joinToString(" ") { it.text }
        "$text $labels"
    }.getOrDefault("")
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    repo: MediaRepository,
    private val dao: GalleryDao,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {
    val filters = MutableStateFlow(SearchFilters())
    private val index = MutableStateFlow<Map<Long, String>>(emptyMap())
    val indexing = MutableStateFlow(false)
    val results = combine(repo.items, filters, index) { items, f, idx ->
        if (f == SearchFilters()) emptyList() else items.filter(f, idx)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val saved = dao.searches(true).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recent = dao.searches(false).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    private val all = repo.items

    fun set(f: SearchFilters) { filters.value = f }
    fun commit(save: Boolean) = viewModelScope.launch { filters.value.query.takeIf { it.isNotBlank() }?.let { dao.addSearch(SavedSearch(query = it, saved = save)) } }
    /** Index up to 150 recent photos (batch job; a persistent index would live in Room). */
    fun buildIndex() = viewModelScope.launch {
        indexing.value = true
        val map = index.value.toMutableMap()
        all.value.filter { !it.isVideo && it.id !in map }.take(150).forEach { map[it.id] = describe(ctx, it); index.value = map.toMap() }
        indexing.value = false
    }
}

@Composable
fun SearchScreen(onOpen: (Long) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val f by vm.filters.collectAsState()
    val results by vm.results.collectAsState()
    val saved by vm.saved.collectAsState()
    val recent by vm.recent.collectAsState()
    val indexing by vm.indexing.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(f.query, { vm.set(f.copy(query = it)) }, Modifier.fillMaxWidth(), label = { Text("Search names, albums, text in photos, objects") }, singleLine = true)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaKind.entries.forEach { k -> FilterChip(f.kind == k, { vm.set(f.copy(kind = if (f.kind == k) null else k)) }, { Text(k.name.lowercase()) }) }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val day = 86_400_000L
                    FilterChip(f.fromMs != null, { vm.set(f.copy(fromMs = if (f.fromMs == null) System.currentTimeMillis() - 30 * day else null)) }, { Text("Last 30 days") })
                    FilterChip(f.minMegapixels > 0, { vm.set(f.copy(minMegapixels = if (f.minMegapixels > 0) 0f else 12f)) }, { Text("≥ 12 MP") })
                    FilterChip(f.minSizeBytes > 0, { vm.set(f.copy(minSizeBytes = if (f.minSizeBytes > 0) 0 else 5_000_000)) }, { Text("≥ 5 MB") })
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton({ vm.commit(true) }) { Text("Save search") }
                    TextButton({ vm.buildIndex() }, enabled = !indexing) { Text(if (indexing) "Scanning…" else "Scan text & objects") }
                }
            }
        }
        if (f == SearchFilters()) {
            Column(Modifier.padding(8.dp)) {
                if (saved.isNotEmpty()) { Text("Saved", style = MaterialTheme.typography.titleSmall); saved.forEach { TextButton({ vm.set(f.copy(query = it.query)) }) { Text(it.query) } } }
                if (recent.isNotEmpty()) { Text("Recent", style = MaterialTheme.typography.titleSmall); recent.forEach { TextButton({ vm.set(f.copy(query = it.query)) }) { Text(it.query) } } }
            }
        }
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(results, key = { it.id }) { m ->
                AsyncImage(m.uri, m.name, Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { onOpen(m.id) }, contentScale = ContentScale.Crop)
            }
        }
    }
}
