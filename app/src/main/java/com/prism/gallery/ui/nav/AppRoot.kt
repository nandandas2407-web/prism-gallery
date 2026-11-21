package com.prism.gallery.ui.nav

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prism.gallery.ui.albums.AlbumsScreen
import com.prism.gallery.ui.components.*
import com.prism.gallery.ui.editor.EditorScreen
import com.prism.gallery.ui.photos.PhotosScreen
import com.prism.gallery.ui.search.SearchScreen
import com.prism.gallery.ui.settings.SettingsScreen
import com.prism.gallery.ui.theme.LocalThemeSettings
import com.prism.gallery.ui.vault.PrivateScreen
import com.prism.gallery.ui.video.PlayerScreen
import com.prism.gallery.ui.viewer.ViewerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("photos", "Photos", Icons.Outlined.PhotoLibrary),
    Tab("albums", "Albums", Icons.Outlined.Collections),
    Tab("search", "Search", Icons.Outlined.Search),
    Tab("private", "Private", Icons.Outlined.Lock),
)

private fun NavGraphBuilder.screen(
    route: String,
    args: List<NamedNavArgument> = emptyList(),
    reduceMotion: Boolean,
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(
    route, args,
    enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(spring()) },
    exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(spring()) },
) { entry -> CompositionLocalProvider(LocalAnimScope provides this) { content(entry) } }

@Composable
fun AppRoot() {
    val activity = LocalContext.current as Activity
    val widthClass = calculateWindowSizeClass(activity).widthSizeClass
    val compact = widthClass == WindowWidthSizeClass.Compact
    val reduce = LocalThemeSettings.current.reduceMotion
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBars = route != null && (route in tabs.map { it.route } || route == "favorites" || route == "settings")
    val go: (String) -> Unit = { r ->
        nav.navigate(r) { launchSingleTop = true; popUpTo("photos") { saveState = true }; restoreState = true }
    }

    GlassBackdrop(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (!compact && showBars) NavRail(route, go)
            SharedTransitionLayout(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalSharedScope provides this) {
                    Box(Modifier.fillMaxSize()) {
                        NavHost(nav, "photos") {
                            screen("photos", reduceMotion = reduce) {
                                PhotosScreen(onOpen = { nav.navigate("viewer/$it") }, onSettings = { nav.navigate("settings") })
                            }
                            screen("favorites", reduceMotion = reduce) {
                                PhotosScreen(favoritesOnly = true, onOpen = { nav.navigate("viewer/$it") }, onSettings = {})
                            }
                            screen("albums", reduceMotion = reduce) {
                                AlbumsScreen(onAlbum = { nav.navigate("album/${android.net.Uri.encode(it)}") }, onFavorites = { nav.navigate("favorites") })
                            }
                            screen("album/{bucket}", listOf(navArgument("bucket") { type = NavType.StringType }), reduce) {
                                PhotosScreen(bucket = it.arguments?.getString("bucket"), onOpen = { id -> nav.navigate("viewer/$id") }, onSettings = {})
                            }
                            screen("search", reduceMotion = reduce) { Narrow { SearchScreen(onOpen = { nav.navigate("viewer/$it") }) } }
                            screen("private", reduceMotion = reduce) { Narrow(960) { PrivateScreen() } }
                            screen("settings", reduceMotion = reduce) { Narrow { SettingsScreen() } }
                            screen("viewer/{id}", listOf(navArgument("id") { type = NavType.LongType }), reduce) {
                                ViewerScreen(
                                    startId = it.arguments!!.getLong("id"),
                                    onClose = { nav.popBackStack() },
                                    onPlay = { id -> nav.navigate("player/$id") },
                                    onEdit = { id -> nav.navigate("editor/$id") },
                                )
                            }
                            screen("player/{id}", listOf(navArgument("id") { type = NavType.LongType }), reduce) {
                                PlayerScreen(it.arguments!!.getLong("id"), onClose = { nav.popBackStack() })
                            }
                            screen("editor/{id}", listOf(navArgument("id") { type = NavType.LongType }), reduce) {
                                EditorScreen(it.arguments!!.getLong("id"), onClose = { nav.popBackStack() })
                            }
                        }
                        if (compact && showBars) {
                            GlassNavBar(route, go, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassNavBar(route: String?, go: (String) -> Unit, modifier: Modifier) {
    GlassSurface(modifier.fillMaxWidth(), RoundedCornerShape(32.dp)) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { t ->
                val sel = route == t.route
                GlassSurface(
                    Modifier.weight(1f).hapticClickable(t.label) { go(t.route) },
                    RoundedCornerShape(26.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else Color.Transparent)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Keeps forms and settings readable on tablets instead of stretching edge to edge. */
@Composable
private fun Narrow(maxWidth: Int = 720, content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) { Box(Modifier.widthIn(max = maxWidth.dp).fillMaxSize()) { content() } }

@Composable
private fun NavRail(route: String?, go: (String) -> Unit) {
    val items = tabs + Tab("settings", "Settings", Icons.Outlined.Settings)
    GlassSurface(Modifier.fillMaxHeight().padding(12.dp).width(104.dp), RoundedCornerShape(32.dp)) {
        Column(Modifier.fillMaxHeight().padding(vertical = 12.dp, horizontal = 8.dp), Arrangement.spacedBy(10.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
            items.forEach { t ->
                val sel = route == t.route
                val tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else Color.Transparent)
                        .hapticClickable(t.label) { go(t.route) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, null, Modifier.size(28.dp), tint = tint)
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
                }
            }
        }
    }
}
