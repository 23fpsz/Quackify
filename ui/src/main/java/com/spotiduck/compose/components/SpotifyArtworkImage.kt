package com.spotiduck.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.spotiduck.compose.theme.SpotifyTheme

/**
 * Resolves any Spotify image format (full URL, spotify:image:<id>, or raw hex image hash)
 * into a valid HTTPS URL pointing to Spotify's image CDN.
 */
fun resolveSpotifyImageUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    val trimmed = rawUrl.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("file://", ignoreCase = true) || trimmed.startsWith("content://", ignoreCase = true) -> trimmed
        trimmed.startsWith("spotify:image:", ignoreCase = true) -> {
            val id = trimmed.substringAfter("spotify:image:")
            "https://i.scdn.co/image/$id"
        }
        // Spotify image ID is typically a 40 or 32 character hex string
        trimmed.length in 30..50 && trimmed.all { it.isDigit() || (it in 'a'..'f') || (it in 'A'..'F') } -> {
            "https://i.scdn.co/image/$trimmed"
        }
        else -> trimmed
    }
}

/**
 * Universal Spotify Album & Playlist Artwork Component.
 * Renders high-resolution remote artwork with crossfade transitions and
 * elegant fallback placeholders.
 */
@Composable
fun SpotifyArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    cornerRadius: Dp? = null,
    fallbackColor: Color = Color(0xFF282828),
    contentScale: ContentScale = ContentScale.Crop
) {
    val effectiveShape = shape ?: (if (cornerRadius != null && cornerRadius > 0.dp) RoundedCornerShape(cornerRadius) else null)
    val clipModifier = if (effectiveShape != null) modifier.clip(effectiveShape) else modifier
    val resolvedUrl = resolveSpotifyImageUrl(imageUrl)

    Box(
        modifier = clipModifier.background(fallbackColor),
        contentAlignment = Alignment.Center
    ) {
        if (!resolvedUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "♪",
                style = SpotifyTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }
    }
}
