package com.prism.gallery.ui.albums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.prism.gallery.data.media.MediaRepository
import com.prism.gallery.ui.components.GlassSurface
import com.prism.gallery.ui.components.hapticClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(repo: MediaRepository) : ViewModel() { val items = repo.items }

@Composable
fun AlbumsScreen(onAlbum: (String) -> Unit, onFavorites: () -> Unit, vm: AlbumsViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    val albums = items.groupBy { it.bucket }.entries.sortedByDescending { it.value.size }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Albums", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
        GlassSurface(Modifier.fillMaxWidth().padding(bottom = 10.dp).hapticClickable("Favorites", onFavorites)) {
            Text("★ Favorites", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        }
        LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(bottom = 120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(albums, key = { it.key }) { (name, list) ->
                GlassSurface(Modifier.hapticClickable("Album $name, ${list.size} items") { onAlbum(name) }, RoundedCornerShape(22.dp)) {
                    Column {
                        AsyncImage(list.first().uri, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
                        Text(name, Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("${list.size}", Modifier.padding(start = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
