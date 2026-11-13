package com.quackify.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quackify.app.search.decorateSearchTracks
import com.quackify.backend.api.Browse
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.api.Library
import com.quackify.backend.api.Podcasts
import com.quackify.backend.api.Profile
import com.quackify.backend.api.Search
import com.quackify.backend.api.Settings
import com.quackify.backend.api.WhatsNew
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.webview.SilentTokenRefresh
import com.quackify.backend.library.Krespot
import com.quackify.backend.library.PlaylistSource
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.Credits
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.HomeSection
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.model.ProfileContext
import com.quackify.backend.model.ProfileTopContent
import com.quackify.backend.model.ProfileUser
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.backend.model.ShelfKind
import com.quackify.backend.model.ShowDetail
import com.quackify.backend.model.TrackLyrics
import com.quackify.backend.model.UserProfile
import com.quackify.compose.components.UpdateDialog
import com.quackify.compose.components.UpdateInstallPhase
import com.quackify.compose.format.formatCount
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.home.toArtistRelease
import com.quackify.compose.home.toFavoriteArtistsShelf
import com.quackify.compose.home.toQuickAccess
import com.quackify.compose.home.toQuickAccessFallback
import com.quackify.compose.home.toRecentlyPlayedShelf
import com.quackify.compose.home.toShelves
import com.quackify.compose.home.toTrackItem
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.AppUpdate
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.CategoryRecommendationItem
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.EncoreNotification
import com.quackify.compose.screens.HistoryStats
import com.quackify.compose.screens.LoginScreen
import com.quackify.compose.screens.PathfinderCaptureScreen
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.ProfileContextCard
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.shell.EncoreAppShell
import com.quackify.compose.shell.PlaylistEditDraft
import com.quackify.compose.shell.ShellDeepLink
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.util.rememberIsOffline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar



private val log = Logs.of(Logs.Module.MAIN)

internal val LAUNCHER_ALIASES = linkedMapOf(
    "default" to ".LauncherDefault",
    "normal" to ".LauncherNormal",
)

internal fun launcherIconPref(context: Context): String {
    val saved = com.quackify.backend.core.store.AppPrefs.prefs(context, "quackify.client")
        .getString("appIcon", "default").orEmpty().ifBlank { "default" }
    return if (saved in LAUNCHER_ALIASES) saved else "default"
}

internal fun applyLauncherIcon(context: Context, option: String) {
    val pm = context.packageManager
    val target = LAUNCHER_ALIASES[option] ?: return
    val prefix = MainActivity::class.java.name.substringBeforeLast('.')
    LAUNCHER_ALIASES.forEach { (key, cls) ->
        val component = ComponentName(context, "$prefix$cls")
        val want = if (key == option) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (pm.getComponentEnabledSetting(component) != want) {
            runCatching {
                pm.setComponentEnabledSetting(component, want, PackageManager.DONT_KILL_APP)
            }.onFailure { log.w("launcher icon '$key' -> ${want == PackageManager.COMPONENT_ENABLED_STATE_ENABLED} failed: $it") }
        }
    }
    log.i("launcher icon -> $option")
}

internal fun syncLauncherIcon(context: Context) {
    applyLauncherIcon(context, launcherIconPref(context))
}

