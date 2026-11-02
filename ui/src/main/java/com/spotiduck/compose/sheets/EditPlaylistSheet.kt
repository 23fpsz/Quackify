package com.spotiduck.compose.sheets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

private const val NAME_LIMIT = 100
private const val DESCRIPTION_LIMIT = 300
private const val COVER_SIDE = 640

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistSheet(
    isOpen: Boolean,
    initialName: String,
    initialDescription: String,
    coverUrl: String?,
    canRemoveCover: Boolean,
    onSave: (name: String, description: String, cover: ByteArray?, clearCover: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var pickedFailed by remember { mutableStateOf(false) }
    var removeCover by remember { mutableStateOf(false) }
    var decoding by remember { mutableStateOf(false) }

    val previewBitmap = remember(picked) {
        picked?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        decoding = true
        pickedFailed = false
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) { squareJpeg(context, uri) }
            decoding = false
            if (jpeg == null) {
                pickedFailed = true
            } else {
                picked = jpeg
                removeCover = false
            }
        }
    }

    val hasCover = picked != null || (!removeCover && !coverUrl.isNullOrBlank())
    val canRemove = picked != null || (canRemoveCover && !removeCover)
    val canSave = name.isNotBlank() && !decoding

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EncoreColors.TextSecondary.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Edit details",
                style = SpotifyTheme.typography.titleLarge.copy(fontSize = 20.sp),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(EncoreColors.PureBlack.copy(alpha = 0.4f))
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        decoding -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = EncoreColors.TextSecondary,
                            strokeWidth = 2.dp
                        )
                        previewBitmap != null -> Image(
                            bitmap = previewBitmap,
                            contentDescription = "New cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                        hasCover && !coverUrl.isNullOrBlank() -> SpotifyArtworkImage(
                            imageUrl = coverUrl,
                            contentDescription = "Current cover",
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 6.dp
                        )
                        else -> Icon(
                            painter = painterResource(id = R.drawable.encore_icon_playlist_24),
                            contentDescription = null,
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasCover) "Change photo" else "Choose photo",
                        style = SpotifyTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier
                            .clickable { picker.launch("image/*") }
                            .padding(vertical = 6.dp)
                    )
                    if (canRemove) {
                        Text(
                            text = "Remove photo",
                            style = SpotifyTheme.typography.bodySmall.copy(
                                color = EncoreColors.TextSecondary
                            ),
                            modifier = Modifier
                                .clickable {
                                    picked = null
                                    if (canRemoveCover) removeCover = true
                                }
                                .padding(vertical = 6.dp)
                        )
                    }
                    if (pickedFailed) {
                        Text(
                            text = "That image could not be read",
                            style = SpotifyTheme.typography.bodySmall.copy(color = Color(0xFFFF6B6B))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            EncoreField(
                label = "Name",
                value = name,
                onValueChange = { if (it.length <= NAME_LIMIT) name = it },
                placeholder = "Add a name",
                singleLine = true,
                counter = "${name.length}/$NAME_LIMIT"
            )

            Spacer(modifier = Modifier.height(14.dp))

            EncoreField(
                label = "Description",
                value = description,
                onValueChange = { if (it.length <= DESCRIPTION_LIMIT) description = it },
                placeholder = "Add an optional description",
                singleLine = false,
                counter = "${description.length}/$DESCRIPTION_LIMIT"
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(500.dp))
                        .background(
                            if (canSave) EncoreColors.Green
                            else EncoreColors.TextSecondary.copy(alpha = 0.3f)
                        )
                        .clickable(enabled = canSave) {
                            onSave(
                                name.trim(),
                                description.trim(),
                                picked,
                                removeCover && picked == null
                            )
                        }
                        .padding(horizontal = 28.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Save",
                        style = SpotifyTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (canSave) Color.Black else EncoreColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EncoreField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    counter: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = SpotifyTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = counter,
                style = SpotifyTheme.typography.marginal.copy(
                    color = EncoreColors.TextSecondary,
                    fontSize = 11.sp
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF282828))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = SpotifyTheme.typography.bodyMedium.copy(
                        color = EncoreColors.TextSecondary,
                        fontSize = 14.sp
                    )
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                maxLines = if (singleLine) 1 else 5,
                cursorBrush = SolidColor(EncoreColors.Green),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun squareJpeg(context: Context, uri: Uri, side: Int = COVER_SIDE): ByteArray? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val edge = min(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(
        decoded,
        (decoded.width - edge) / 2,
        (decoded.height - edge) / 2,
        edge,
        edge
    )
    val scaled = Bitmap.createScaledBitmap(square, side, side, true)
    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
}.getOrNull()
