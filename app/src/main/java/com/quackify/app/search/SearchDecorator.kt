package com.quackify.app.search

import android.content.Context
import com.quackify.backend.library.TrackSource
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults

/**
 * The ranked search answer decorates only its first row fully: every track
 * below the top result can arrive with a bare name and uri, which left the
 * Songs list without covers or artist lines. One decorateContextTracks
 * batch fills exactly those rows before the list reaches the screen.
 */
suspend fun decorateSearchTracks(context: Context, results: SearchResults): SearchResults {
    fun thin(hit: SearchHit) =
        hit.kind == SearchKind.TRACK && (hit.imageUrl.isBlank() || hit.subtitle.isBlank())
    val missing = (listOfNotNull(results.top) + results.tracks)
        .filter(::thin)
        .map { it.uri }
        .distinct()
    if (missing.isEmpty()) return results
    val rows = runCatching { TrackSource.decorate(context, missing) }.getOrDefault(emptyMap())
    if (rows.isEmpty()) return results
    fun fill(hit: SearchHit): SearchHit {
        val row = rows[hit.uri] ?: return hit
        return hit.copy(
            subtitle = hit.subtitle.ifBlank { row.artists },
            imageUrl = hit.imageUrl.ifBlank { row.imageUrl },
            durationMs = row.durationMs.takeIf { it > 0L } ?: hit.durationMs,
            explicit = hit.explicit || row.explicit,
        )
    }
    return results.copy(
        top = results.top?.let(::fill),
        tracks = results.tracks.map(::fill),
    )
}
