package com.quackify.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.components.ShimmerBlock
import com.quackify.compose.components.ShimmerHost
import com.quackify.compose.components.ShimmerText
import com.quackify.compose.components.rememberSmoothColor
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.backend.api.Browse
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.BrowseSection

@Composable
fun SpotifyBrowseScreen(
    title: String,
    subtitle: String,
    colorHex: String,
    sections: List<BrowseSection>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onBackClick: () -> Unit,
    onCardClick: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent by rememberSmoothColor(target = parseHexColor(colorHex) ?: EncoreColors.Card)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.22f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_chevron_left_24,
                contentDescription = "Back",
                onClick = onBackClick,
                size = 48.dp,
                iconSize = 24.dp
            )
            Text(
                text = title,
                style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (isLoading && sections.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    ShimmerHost {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                            ShimmerText(widthFraction = 0.55f, height = 30.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            ShimmerText(widthFraction = 0.35f, height = 13.dp)
                            Spacer(modifier = Modifier.height(28.dp))
                            repeat(2) { index ->
                                ShimmerText(
                                    widthFraction = 0.4f,
                                    height = 20.dp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    repeat(3) {
                                        ShimmerBlock(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .height(150.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(if (index == 0) 22.dp else 0.dp))
                            }
                        }
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = 0.55f), EncoreColors.Background)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = title,
                        style = SpotifyTheme.typography.headlineLarge.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = EncoreColors.TextPrimary
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = SpotifyTheme.typography.bodyMedium.copy(
                                color = EncoreColors.TextSecondary
                            )
                        )
                    }
                }
            }

            items(sections) { section ->
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    if (section.title.isNotBlank()) {
                        Text(
                            text = section.title,
                            style = SpotifyTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = EncoreColors.TextPrimary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp)
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(section.cards) { card ->
                            BrowseCardTile(card = card, onClick = { onCardClick(card.uri, card.title) })
                        }
                    }
                }
            }

            if (hasMore) {
                item {
                    LaunchedEffect(sections.size) {
                        if (!isLoadingMore) onLoadMore()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InlineLoadingDots(color = EncoreColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseCardTile(card: BrowseCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        SpotifyArtworkImage(
            imageUrl = card.imageUrl,
            contentDescription = card.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 6.dp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = card.title,
            style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = EncoreColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (card.subtitle.isNotBlank()) {
            Text(
                text = card.subtitle,
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
