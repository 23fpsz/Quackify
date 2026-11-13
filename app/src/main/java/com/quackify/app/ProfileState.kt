/*
 * SpotiDuck-Compose — profile screen state holder.
 *
 * Owns everything needed to render the profile screen: the profile itself,
 * self-profile top content and recently played contexts, and the follower /
 * following lists. Moved out of MainActivity's shell composable both for
 * cohesion and to keep that composable under the compiler's per-method size
 * limit.
 */

package com.quackify.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quackify.backend.api.Profile
import com.quackify.backend.core.log.Logs
import com.quackify.backend.library.Krespot
import com.quackify.backend.model.ProfileContext
import com.quackify.backend.model.ProfileTopContent
import com.quackify.backend.model.ProfileUser
import com.quackify.backend.model.UserProfile
import com.quackify.compose.screens.ProfileContextCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private val log = Logs.of(Logs.Module.MAIN)

class ProfileState {
    var detail by mutableStateOf<UserProfile?>(null)
        internal set
    var top by mutableStateOf<ProfileTopContent?>(null)
        internal set
    var contexts by mutableStateOf<List<ProfileContextCard>>(emptyList())
        internal set
    var loading by mutableStateOf(false)
        internal set
    var failed by mutableStateOf(false)
        internal set
    var userId by mutableStateOf("")
        internal set
    var followers by mutableStateOf<List<ProfileUser>>(emptyList())
        internal set
    var following by mutableStateOf<List<ProfileUser>>(emptyList())
        internal set
    var followLoading by mutableStateOf(false)
        internal set

    /** Nonce bumped by every load request; 0 means "never loaded". */
    var fetch by mutableStateOf(0)
        internal set
    var followFetch by mutableStateOf(0)
        internal set

    /** Opens "My profile"; clears any open user target first. */
    fun openMyProfile() {
        if (userId.isNotBlank()) {
            userId = ""
            clearContent()
        }
        fetch++
    }

    fun retry() {
        clearContent()
        fetch++
    }

    fun openUser(uri: String) {
        if (userId != uri) {
            userId = uri
            clearContent()
        }
        fetch++
    }

    fun loadFollows() {
        followFetch++
    }

    private fun clearContent() {
        detail = null
        top = null
        contexts = emptyList()
    }
}

@Composable
fun rememberProfileState(context: Context): ProfileState {
    val state = remember { ProfileState() }
    LaunchedEffect(state.fetch) {
        if (state.fetch == 0) return@LaunchedEffect
        state.loading = true
        state.failed = false
        coroutineScope {
            val target = state.userId
            val profile = async { runCatching { Profile.profile(context, target) }.getOrNull() }.await()
            val isSelf = target.isBlank() || profile?.isCurrentUser == true
            val (top, recent) = if (isSelf) {
                val topJob = async { runCatching { Profile.topContent(context) }.getOrNull() }
                val recentJob = async { runCatching { Profile.recentlyPlayed(context) }.getOrNull() }
                topJob.await() to recentJob.await()
            } else {
                null to null
            }
            state.detail = profile
            state.top = top
            state.contexts = if (isSelf) {
                resolveProfileContexts(context, profile, recent)
            } else {
                profile?.publicPlaylists.orEmpty().map {
                    ProfileContextCard(
                        uri = it.uri,
                        title = it.name,
                        subtitle = "Playlist",
                        imageUrl = it.imageUrl,
                    )
                }
            }
            state.failed = profile == null && top == null
            log.i("profile loaded self=$isSelf failed=${profile == null} " +
                    "topArtists=${top?.topArtists?.size ?: 0} " +
                    "contexts=${state.contexts.size}"
            )
        }
        state.loading = false
    }
    LaunchedEffect(state.followFetch) {
        if (state.followFetch == 0) return@LaunchedEffect
        state.followLoading = true
        coroutineScope {
            val id = state.userId
            val followersJob = async { runCatching { Profile.followers(context, id) }.getOrNull() }
            val followingJob = async { runCatching { Profile.following(context, id) }.getOrNull() }
            state.followers = followersJob.await().orEmpty()
            state.following = followingJob.await().orEmpty()
        }
        state.followLoading = false
    }
    return state
}

private const val PROFILE_CONTEXT_LIMIT = 12

/**
 * The recently-played feed only carries context uris, so each entry is turned
 * into a displayable card: artists come straight from the profile response,
 * playlists and albums are resolved through their existing sources.
 */
private suspend fun resolveProfileContexts(
    context: Context,
    profile: UserProfile?,
    contexts: List<ProfileContext>?,
): List<ProfileContextCard> {
    if (contexts.isNullOrEmpty()) return emptyList()
    val artistByUri = profile?.recentlyPlayedArtists?.associateBy { it.uri }.orEmpty()
    return contexts.take(PROFILE_CONTEXT_LIMIT).mapNotNull { entry ->
        when {
            entry.uri.startsWith("spotify:artist:") -> {
                val artist = artistByUri[entry.uri] ?: return@mapNotNull null
                ProfileContextCard(entry.uri, artist.name, "Artist", artist.imageUrl)
            }
            entry.uri.startsWith("spotify:playlist:") -> {
                val detail = runCatching {
                    Krespot.collections.collection(context, entry.uri)
                }.getOrNull() ?: return@mapNotNull null
                ProfileContextCard(entry.uri, detail.title, "Playlist", detail.imageUrl)
            }
            entry.uri.startsWith("spotify:album:") -> {
                val detail = runCatching {
                    Krespot.albums.album(context, entry.uri)
                }.getOrNull() ?: return@mapNotNull null
                ProfileContextCard(entry.uri, detail.title, detail.artists, detail.imageUrl)
            }
            else -> null
        }
    }
}
