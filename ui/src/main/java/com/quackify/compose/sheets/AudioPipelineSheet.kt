package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.shell.PipelineInfo
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import java.util.Locale

/**
 * The now-playing audio pipeline: which source is serving the stream and
 * what the decoder is actually being fed, measured figures first with the
 * source's declared ones beside them when they differ. Rows the player has
 * no answer for are left out rather than shown empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPipelineSheet(
    pipeline: PipelineInfo,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = Color(0xFF242424),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.audio_pipeline),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (pipeline.sourceKey.isBlank()) {
                Text(
                    text = stringResource(R.string.pipeline_no_stream),
                    style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                if (!pipeline.hasDecoder) {
                    Text(
                        text = stringResource(R.string.pipeline_pending),
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                PipelineRow(
                    label = stringResource(R.string.pipeline_source),
                    value = sourceLabel(pipeline.sourceKey)
                )

                codecRow(pipeline)?.let { value ->
                    PipelineRow(
                        label = stringResource(R.string.pipeline_codec),
                        value = value
                    )
                }

                bitrateRow(pipeline)?.let { (value, detail) ->
                    PipelineRow(
                        label = stringResource(R.string.pipeline_bitrate),
                        value = value,
                        detail = detail
                    )
                }

                sampleRateLabel(pipeline.sampleRateHz)?.let { value ->
                    PipelineRow(
                        label = stringResource(R.string.pipeline_sample_rate),
                        value = value
                    )
                }

                channelLabel(pipeline.channels)?.let { value ->
                    PipelineRow(
                        label = stringResource(R.string.pipeline_channels),
                        value = value
                    )
                }

                if (pipeline.bitDepth > 0) {
                    PipelineRow(
                        label = stringResource(R.string.pipeline_bit_depth),
                        value = "${pipeline.bitDepth}-bit"
                    )
                }

                streamIdLabel(pipeline)?.let { value ->
                    PipelineRow(
                        label = stringResource(R.string.pipeline_stream_id),
                        value = value
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun sourceLabel(sourceKey: String): String = when (sourceKey) {
    "spotify" -> stringResource(R.string.pipeline_source_spotify)
    "ytmusic" -> stringResource(R.string.pipeline_source_ytmusic)
    "music_video" -> stringResource(R.string.pipeline_source_video)
    else -> sourceKey
}

private fun codecRow(pipeline: PipelineInfo): String? {
    val mime = pipeline.mimeType
    if (mime.isBlank()) return null
    val codec = when {
        mime.endsWith("mp4a-latm", ignoreCase = true) -> "AAC"
        mime.endsWith("aac", ignoreCase = true) -> "AAC"
        mime.endsWith("opus", ignoreCase = true) -> "Opus"
        mime == "audio/mpeg" -> "MP3"
        mime.endsWith("mp3", ignoreCase = true) -> "MP3"
        mime.endsWith("vorbis", ignoreCase = true) -> "Vorbis"
        mime.endsWith("flac", ignoreCase = true) -> "FLAC"
        mime.endsWith("eac3-joc", ignoreCase = true) -> "E-AC-3 JOC"
        mime.endsWith("eac3", ignoreCase = true) -> "E-AC-3"
        mime == "audio/raw" || mime.contains("pcm", ignoreCase = true) -> "PCM"
        mime.contains("/") -> mime.substringAfter("/").uppercase(Locale.ROOT)
        else -> mime.uppercase(Locale.ROOT)
    }
    return listOfNotNull(codec, pipeline.codecs.takeIf { it.isNotBlank() }).joinToString(" · ")
}

@Composable
private fun bitrateRow(pipeline: PipelineInfo): Pair<String, String?>? {
    val decoder = pipeline.decoderBitrateBps / 1000
    val declared = pipeline.declaredBitrateBps / 1000
    val value = when {
        decoder > 0 -> "$decoder kbps"
        declared > 0 -> "$declared kbps"
        else -> return null
    }
    val details = ArrayList<String>(2)
    if (decoder > 0 && declared > 0 && declared != decoder) {
        details += stringResource(R.string.pipeline_declared, declared)
    }
    if (pipeline.declaredQuality.isNotBlank()) {
        details += pipeline.declaredQuality
    }
    return value to details.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun sampleRateLabel(hz: Int): String? {
    if (hz <= 0) return null
    return if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.ROOT, "%.1f kHz", hz / 1000f)
    }
}

@Composable
private fun channelLabel(channels: Int): String? = when {
    channels <= 0 -> null
    channels == 1 -> stringResource(R.string.pipeline_channels_mono)
    channels == 2 -> stringResource(R.string.pipeline_channels_stereo)
    else -> stringResource(R.string.pipeline_channels_multi, channels)
}

private fun streamIdLabel(pipeline: PipelineInfo): String? = when {
    pipeline.fileId.isNotBlank() -> pipeline.fileId.take(12)
    pipeline.videoId.isNotBlank() -> pipeline.videoId
    else -> null
}

@Composable
private fun PipelineRow(
    label: String,
    value: String,
    detail: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = value,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = EncoreColors.TextPrimary
                ),
                textAlign = TextAlign.End
            )
        }
        detail?.let {
            Text(
                text = it,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
