package com.spotiduck.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme

enum class LibrarySortOption(val displayName: String, val apiId: String) {
    RECENTS("Recents", "Recents"),
    RECENTLY_ADDED("Recently added", "Recently Added"),
    ALPHABETICAL("Alphabetical", "Alphabetical"),
    CREATOR("Creator", "Creator");

    companion object {
        fun fromApiId(id: String?): LibrarySortOption? =
            values().firstOrNull { it.apiId.equals(id, ignoreCase = true) }
    }
}

/**
 * Spotify Library Sort & Filter Bottom Sheet matching decompiled:
 * - `sort_filter_bottom_sheet_layout.xml`
 * - `sort_and_filter_bottom_sheet_handle.xml` (36dp x 4dp grapple, #4DFFFFFF)
 * - `@style/CwpSortAndFilterTitleStyle` (48dp minHeight, 16dp horizontal padding)
 * - `@style/CwpSortAndFilterOptionStyle` (BodyMedium, 48dp minHeight, Green text + checkmark for active)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySortSheet(
    isOpen: Boolean,
    currentSort: LibrarySortOption,
    onSortSelected: (LibrarySortOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            // sort_and_filter_bottom_sheet_handle.xml: 36dp x 4dp rounded clip, #4DFFFFFF
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x4DFFFFFF))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Centered Header Title matching @string/sort_and_filter_sort_group_title ("Sort by")
            Text(
                text = "Sort by",
                style = SpotifyTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Divider matching sort_filter_bottom_sheet_layout.xml: 1dp ?backgroundTintedHighlight
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x1AFFFFFF))
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Sort Options styled with CwpSortAndFilterOptionStyle
            LibrarySortOption.values().forEach { option ->
                val isSelected = option == currentSort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            onSortSelected(option)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.displayName,
                        style = SpotifyTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) EncoreColors.Green else EncoreColors.TextPrimary,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_check_24),
                            contentDescription = "Selected",
                            tint = EncoreColors.Green,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
fun PreviewLibrarySortSheet() {
    SpotifyEncoreTheme {
        LibrarySortSheet(
            isOpen = true,
            currentSort = LibrarySortOption.RECENTS,
            onSortSelected = {},
            onDismiss = {}
        )
    }
}
