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

    Box(
        modifier = clipModifier.background(fallbackColor),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
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
