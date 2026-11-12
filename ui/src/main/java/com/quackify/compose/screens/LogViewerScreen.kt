package com.quackify.compose.screens

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.core.log.DeviceInfo
import com.quackify.compose.sheets.DarkSystemBarsSheet
import com.quackify.backend.core.log.Logs
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.theme.DmMono
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ViewerBackground = Color(0xFF0A0A0A)
private val LevelVerbose = Color(0xFF9E9E9E)
private val LevelDebug = Color(0xFF6FB7FF)
private val LevelInfo = Color(0xFF1ED760)
private val LevelWarn = Color(0xFFFFB84D)
private val LevelError = Color(0xFFFF5A5F)

/**
 * Hidden logcat-style viewer over the in-memory ring buffer kept by [Logs].
 * Opened by tapping the Version row in Settings five times, or from the
 * small LOGS pill on the login screen. Lines read top to bottom (oldest
 * first, like a chat transcript) and new lines land at the bottom, where
 * the Auto-scroll toggle can follow them. The Logging toggle only pauses
 * the in-app capture (this screen's ring buffer); logcat output always
 * flows, so Android Studio keeps showing logs either way.
 */
@Composable
fun LogViewerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val revision by Logs.revision.collectAsState()
    val bottomPadding = rememberBottomPadding()
    var levelFilter by remember { mutableStateOf<Logs.Level?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Logs.Entry?>(null) }
    var clearAsk by remember { mutableStateOf(false) }
    var deviceInfoOpen by remember { mutableStateOf(false) }
    var captureOn by remember { mutableStateOf(Logs.capturing) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        Logs.of(Logs.Module.SHELL).i("log viewer opened, ${Logs.size()} lines buffered")
    }

    val all = remember(revision) { Logs.snapshot() }
    val entries = remember(all, levelFilter, query) {
        val needle = query.trim()
        all.filter { entry ->
            (levelFilter == null || entry.level == levelFilter) &&
                (needle.isEmpty() ||
                    entry.message.contains(needle, ignoreCase = true) ||
                    entry.module.label.contains(needle, ignoreCase = true) ||
                    (entry.error?.contains(needle, ignoreCase = true) == true))
        }
    }

    LaunchedEffect(revision, autoScroll) {
        if (autoScroll && entries.isNotEmpty()) {
            listState.scrollToItem(entries.size + 1)
        }
    }

    fun setCapture(next: Boolean) {
        if (next == captureOn) return
        val prefs = com.quackify.backend.core.store.AppPrefs.prefs(context, Logs.PREFS_NAME)
        if (next) {
            Logs.capturing = true
            prefs.edit().putBoolean(Logs.CAPTURE_PREF_KEY, true).apply()
            captureOn = true
            Logs.of(Logs.Module.SHELL).i("log capture resumed, ${Logs.size()} lines kept")
        } else {
            Logs.of(Logs.Module.SHELL).i("log capture paused by user")
            prefs.edit().putBoolean(Logs.CAPTURE_PREF_KEY, false).apply()
            Logs.capturing = false
            captureOn = false
        }
    }

    fun copy(text: String, note: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Quackify logs", text))
        Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = "Back",
                onClick = onBackClick,
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Logs",
                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp)
                )
                Text(
                    text = if (captureOn) {
                        "${all.size}/${Logs.MAX_LINES} lines kept · oldest first"
                    } else {
                        "capture paused · ${all.size}/${Logs.MAX_LINES} lines kept"
                    },
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (entries.size == all.size) "" else "${entries.size} shown",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.Green),
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogSearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_copy_24,
                contentDescription = "Copy all",
                onClick = {
                    if (entries.isEmpty()) {
                        Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                    } else {
                        copy(entries.joinToString("\n") { Logs.format(it) }, "Copied ${entries.size} lines")
                    }
                }
            )
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_download_24,
                contentDescription = "Save to file",
                onClick = {
                    if (entries.isEmpty()) {
                        Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
                    } else {
                        val body = entries.joinToString("\n") { Logs.format(it) }
                        val saved = saveTextFile(context, "quackify_log", body)
                        Toast.makeText(
                            context,
                            saved?.let { "Saved to $it" } ?: "Couldn't save the file",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_devices_24,
                contentDescription = "Export device information",
                onClick = { deviceInfoOpen = true }
            )
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_trash_24,
                contentDescription = "Clear",
                onClick = { clearAsk = true }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LevelChip(label = "All", selected = levelFilter == null) { levelFilter = null }
            LevelChip(label = "V", selected = levelFilter == Logs.Level.VERBOSE) { levelFilter = Logs.Level.VERBOSE }
            LevelChip(label = "D", selected = levelFilter == Logs.Level.DEBUG) { levelFilter = Logs.Level.DEBUG }
            LevelChip(label = "I", selected = levelFilter == Logs.Level.INFO) { levelFilter = Logs.Level.INFO }
            LevelChip(label = "W", selected = levelFilter == Logs.Level.WARN) { levelFilter = Logs.Level.WARN }
            LevelChip(label = "E", selected = levelFilter == Logs.Level.ERROR) { levelFilter = Logs.Level.ERROR }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleChip(
                label = "Logging",
                active = captureOn,
                onClick = { setCapture(!captureOn) }
            )
            ToggleChip(
                label = "Auto-scroll",
                active = autoScroll,
                onClick = { autoScroll = !autoScroll }
            )
        }

        HorizontalDivider(color = Color(0xFF1F1F1F), thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (all.isEmpty()) "No logs yet" else "Nothing matches this filter",
                        style = EncoreTheme.typography.titleMedium,
                        color = EncoreColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (all.isEmpty()) {
                            if (captureOn) {
                                "Lines appear as the app runs.\nOpen this screen again by tapping Version five times in Settings."
                            } else {
                                "Capture is paused.\nTurn the Logging chip back on to start recording again."
                            }
                        } else {
                            "Loosen the level filter or the search text."
                        },
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                item(key = "oldest") {
                    LogEdgeMarker(text = "oldest of ${entries.size}")
                }
                items(entries, key = { it.seq }, contentType = { "log" }) { entry ->
                    LogRow(
                        entry = entry,
                        onClick = { selected = entry },
                        onLongClick = { copy(Logs.format(entry), "Line copied") }
                    )
                }
                item(key = "newest") {
                    LogEdgeMarker(text = "newest of ${entries.size}")
                }
            }
        }
    }

    if (clearAsk) {
        AlertDialog(
            onDismissRequest = { clearAsk = false },
            containerColor = EncoreColors.CardElevated,
            title = { Text("Clear logs?", color = EncoreColors.TextPrimary) },
            text = {
                Text(
                    "This drops the ${all.size} captured lines. They cannot be recovered.",
                    color = EncoreColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Logs.clear()
                    clearAsk = false
                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                }) { Text("Clear", color = EncoreColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { clearAsk = false }) { Text("Cancel", color = EncoreColors.TextSecondary) }
            }
        )
    }

    selected?.let { entry ->
        LogDetailSheet(
            entry = entry,
            onDismiss = { selected = null },
            onCopy = { copy(Logs.format(entry), "Entry copied") }
        )
    }

    if (deviceInfoOpen) {
        DeviceInfoSheet(
            onDismiss = { deviceInfoOpen = false },
            onCopy = { copy(it, "Device information copied") },
            onSave = { report ->
                val saved = saveTextFile(context, "quackify_device", report)
                Toast.makeText(
                    context,
                    saved?.let { "Saved to $it" } ?: "Couldn't save the file",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

@Composable
private fun LogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = EncoreTheme.typography.bodySmall.copy(
            color = EncoreColors.TextPrimary,
            fontFamily = DmMono,
            fontSize = 12.sp
        ),
        cursorBrush = Brush.verticalGradient(listOf(EncoreColors.Green, EncoreColors.Green)),
        decorationBox = { inner ->
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EncoreColors.Card)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Filter lines…",
                        style = EncoreTheme.typography.bodySmall.copy(
                            color = EncoreColors.TextSecondary,
                            fontFamily = DmMono,
                            fontSize = 12.sp
                        )
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun LevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) EncoreColors.Green else EncoreColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontFamily = DmMono,
            fontSize = 11.sp,
            color = if (selected) Color(0xFF000000) else EncoreColors.TextSecondary
        )
    }
}

/** Pill toggle with a status dot: green means on, dark gray means off. */
@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) EncoreColors.Green else EncoreColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) Color(0xFF000000) else EncoreColors.TextSecondary)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = DmMono,
            fontSize = 11.sp,
            color = if (active) Color(0xFF000000) else EncoreColors.TextSecondary
        )
    }
}

/** Small centered caption marking either end of the buffered stream. */
@Composable
private fun LogEdgeMarker(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF555555),
            fontFamily = DmMono,
            fontSize = 10.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    entry: Logs.Entry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Text(
        text = annotate(entry),
        fontFamily = DmMono,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

private fun annotate(entry: Logs.Entry): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Color(0xFF707070))) {
        append(SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.wallClockMs)))
    }
    append(' ')
    withStyle(SpanStyle(color = levelColor(entry.level), fontWeight = FontWeight.Bold)) {
        append(entry.levelLetter)
    }
    append(' ')
    withStyle(SpanStyle(color = Color(0xFF8F8F8F))) {
        append("[${entry.module.label}]")
    }
    append(' ')
    withStyle(SpanStyle(color = EncoreColors.TextPrimary)) {
        append(entry.message)
    }
    if (!entry.error.isNullOrBlank()) {
        append(' ')
        withStyle(SpanStyle(color = LevelError)) { append("<!>") }
    }
}

private fun levelColor(level: Logs.Level): Color = when (level) {
    Logs.Level.VERBOSE -> LevelVerbose
    Logs.Level.DEBUG -> LevelDebug
    Logs.Level.INFO -> LevelInfo
    Logs.Level.WARN -> LevelWarn
    Logs.Level.ERROR -> LevelError
    Logs.Level.OFF -> LevelVerbose
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailSheet(
    entry: Logs.Entry,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = Color(0xFF101010),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Log entry",
                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "timestamp",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                fontSize = 11.sp
            )
            Text(
                text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(entry.wallClockMs)) +
                    "  (uptime +${entry.uptimeMs / 1000.0}s)",
                fontFamily = DmMono,
                fontSize = 12.sp,
                color = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "level / module",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                fontSize = 11.sp
            )
            Text(
                text = "${entry.levelLetter} · ${entry.level.name} · ${entry.module.label}",
                fontFamily = DmMono,
                fontSize = 12.sp,
                color = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "message",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                fontSize = 11.sp
            )
            SelectionContainer {
                Text(
                    text = entry.message,
                    fontFamily = DmMono,
                    fontSize = 12.sp,
                    color = EncoreColors.TextPrimary
                )
            }
            val stack = entry.error
            if (!stack.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "stack",
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    fontSize = 11.sp
                )
                SelectionContainer {
                    Text(
                        text = stack,
                        fontFamily = DmMono,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = LevelError
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EncoreColors.Green)
                        .clickable(onClick = onCopy)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Copy entry",
                        color = Color(0xFF000000),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EncoreColors.Card)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        color = EncoreColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceInfoSheet(
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.Default) { runCatching { DeviceInfo.collect(context) }.getOrNull() }
            ?: "The report could not be built."
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = Color(0xFF101010),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Device information",
                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            val current = report
            if (current == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InlineLoadingDots(color = EncoreColors.TextSecondary, dotSize = 6.dp)
                }
            } else {
                SelectionContainer {
                    Text(
                        text = current,
                        fontFamily = DmMono,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = EncoreColors.TextPrimary,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EncoreColors.Green)
                        .clickable(enabled = current != null) { current?.let(onCopy) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Copy",
                        color = Color(0xFF000000),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EncoreColors.Card)
                        .clickable(enabled = current != null) { current?.let(onSave) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save to file",
                        color = EncoreColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EncoreColors.Card)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        color = EncoreColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/** Writes [text] to Downloads/Quackify (API 29+) or the app's external files. */
private fun saveTextFile(context: Context, prefix: String, text: String): String? {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val name = "${prefix}_$stamp.txt"
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Quackify")
            }
            val resolver = context.contentResolver
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } ?: return null
            "Downloads/Quackify/$name"
        } else {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            file.writeText(text, Charsets.UTF_8)
            file.absolutePath
        }
    }.getOrNull()
}

@Preview
@Composable
fun PreviewLogViewerScreen() {
    EncoreAppTheme {
        LogViewerScreen(onBackClick = {})
    }
}
