package com.quackify.compose.sheets

import android.text.format.DateFormat
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import kotlinx.coroutines.delay
import java.util.Date

sealed interface SleepTimerOption {
    val minutes: Int

    data class Timed(override val minutes: Int) : SleepTimerOption

    data object EndOfTrack : SleepTimerOption {
        override val minutes: Int get() = -1
    }

    data object Off : SleepTimerOption {
        override val minutes: Int get() = 0
    }

    companion object {
        const val MIN_CUSTOM_MINUTES = 3
        const val MAX_CUSTOM_MINUTES = 24 * 60

        val MINUTES_5 = Timed(5)
        val MINUTES_10 = Timed(10)
        val MINUTES_15 = Timed(15)
        val MINUTES_30 = Timed(30)
        val MINUTES_45 = Timed(45)
        val MINUTES_60 = Timed(60)

        val presets = listOf(MINUTES_5, MINUTES_10, MINUTES_15, MINUTES_30, MINUTES_45, MINUTES_60)
    }
}

@Composable
fun sleepTimerOptionLabel(option: SleepTimerOption): String = when (option) {
    SleepTimerOption.Off -> stringResource(R.string.turn_off_timer)
    SleepTimerOption.EndOfTrack -> stringResource(R.string.end_of_track)
    is SleepTimerOption.Timed -> presetLabelRes(option.minutes)?.let { stringResource(it) }
        ?: sleepTimerDurationLabel(option.minutes)
}

private fun presetLabelRes(minutes: Int): Int? = when (minutes) {
    5 -> R.string.five_minutes
    10 -> R.string.ten_minutes
    15 -> R.string.fifteen_minutes
    30 -> R.string.thirty_minutes
    45 -> R.string.forty_five_minutes
    60 -> R.string.one_hour
    else -> null
}

@Composable
fun sleepTimerDurationLabel(minutes: Int): String {
    val resources = LocalContext.current.resources
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours <= 0 -> resources.getQuantityString(R.plurals.sleep_timer_minutes, mins, mins)
        mins == 0 -> resources.getQuantityString(R.plurals.sleep_timer_hours, hours, hours)
        else -> resources.getQuantityString(R.plurals.sleep_timer_hours, hours, hours) + " " +
            resources.getQuantityString(R.plurals.sleep_timer_minutes, mins, mins)
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSeconds = (ms + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

/**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    activeOption: SleepTimerOption?,
    endsAtMs: Long = 0L,
    onOptionSelected: (SleepTimerOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timerActive = activeOption != null && activeOption != SleepTimerOption.Off

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMs) {
        if (endsAtMs <= 0L) return@LaunchedEffect
        while (nowMs < endsAtMs) {
            nowMs = System.currentTimeMillis()
            if (nowMs < endsAtMs) delay(1000L)
        }
    }

    val context = LocalContext.current
    val stopsAtLabel = remember(endsAtMs) {
        if (endsAtMs > 0L) DateFormat.getTimeFormat(context).format(Date(endsAtMs)) else null
    }
    val showCountdown = timerActive && activeOption is SleepTimerOption.Timed && endsAtMs > nowMs

    var customExpanded by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }
    val customMinutes = customInput.trim().toIntOrNull()
    val customValid = customMinutes != null &&
        customMinutes in SleepTimerOption.MIN_CUSTOM_MINUTES..SleepTimerOption.MAX_CUSTOM_MINUTES

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.stop_audio_in),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (showCountdown) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatCountdown(endsAtMs - nowMs),
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.Green
                        )
                    )
                    stopsAtLabel?.let {
                        Text(
                            text = stringResource(R.string.sleep_timer_stops_at, it),
                            style = EncoreTheme.typography.bodySmall
                        )
                    }
                }
            } else if (activeOption == SleepTimerOption.EndOfTrack) {
                Text(
                    text = stringResource(R.string.sleep_timer_end_of_track_info),
                    style = EncoreTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            val options = buildList {
                val activeCustom = activeOption as? SleepTimerOption.Timed
                if (activeCustom != null && activeCustom !in SleepTimerOption.presets) {
                    add(activeCustom)
                }
                addAll(SleepTimerOption.presets)
                add(SleepTimerOption.EndOfTrack)
            }

            options.forEach { option ->
                val isSelected = activeOption == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOptionSelected(option)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sleepTimerOptionLabel(option),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EncoreColors.Green else EncoreColors.TextPrimary
                        )
                    )

                    if (isSelected) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_check_24),
                            contentDescription = stringResource(R.string.selected),
                            tint = EncoreColors.Green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!customExpanded) {
                            val activeCustom = activeOption as? SleepTimerOption.Timed
                            if (activeCustom != null && activeCustom !in SleepTimerOption.presets) {
                                customInput = activeCustom.minutes.toString()
                            }
                        }
                        customExpanded = !customExpanded
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sleep_timer_custom),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = if (customExpanded) FontWeight.Bold else FontWeight.Medium,
                        color = if (customExpanded) EncoreColors.Green else EncoreColors.TextPrimary
                    )
                )
                Icon(
                    painter = painterResource(
                        if (customExpanded) R.drawable.encore_icon_chevron_up_24
                        else R.drawable.encore_icon_chevron_down_24
                    ),
                    contentDescription = stringResource(R.string.sleep_timer_custom),
                    tint = if (customExpanded) EncoreColors.Green else EncoreColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (customExpanded) {
                CustomTimerInput(
                    value = customInput,
                    valid = customValid,
                    minutes = customMinutes,
                    onValueChange = { raw ->
                        if (raw.length <= 4 && raw.all { it.isDigit() }) customInput = raw
                    },
                    onSet = {
                        val minutes = customInput.trim().toIntOrNull() ?: return@CustomTimerInput
                        if (minutes in SleepTimerOption.MIN_CUSTOM_MINUTES..SleepTimerOption.MAX_CUSTOM_MINUTES) {
                            onOptionSelected(SleepTimerOption.Timed(minutes))
                            onDismiss()
                        }
                    }
                )
            }

            if (timerActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOptionSelected(SleepTimerOption.Off)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.turn_off_timer),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = EncoreColors.TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CustomTimerInput(
    value: String,
    valid: Boolean,
    minutes: Int?,
    onValueChange: (String) -> Unit,
    onSet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 2.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EncoreColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = EncoreColors.TextPrimary,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(EncoreColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (valid) onSet() }
                    )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.sleep_timer_custom_unit),
                style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
            )
        }

        minutes?.takeIf { it > 0 }?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sleepTimerDurationLabel(it),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextPrimary)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sleep_timer_custom_hint),
            style = EncoreTheme.typography.bodySmall.copy(
                color = if (value.isNotBlank() && !valid) EncoreColors.Error else EncoreColors.TextSecondary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(if (valid) EncoreColors.Green else EncoreColors.Green.copy(alpha = 0.4f))
                .clickable(enabled = valid, onClick = onSet)
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.sleep_timer_set),
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 14.sp
                )
            )
        }
    }
}
