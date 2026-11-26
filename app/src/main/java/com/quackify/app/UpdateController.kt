package com.quackify.app

import android.widget.Toast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.store.AppPrefs
import com.quackify.backend.core.net.Net
import com.quackify.compose.components.UpdateDialog
import com.quackify.compose.screens.AppUpdate
import com.quackify.compose.components.UpdateInstallPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val log = Logs.of(Logs.Module.MAIN)

internal class UpdateController {

    var appUpdate by mutableStateOf<AppUpdate?>(null)
        internal set

    var checking by mutableStateOf(false)
        internal set

    var checked by mutableStateOf(false)
        internal set

    var error by mutableStateOf<String?>(null)
        internal set

    var prompt by mutableStateOf<AppUpdate?>(null)
        internal set

    var phase by mutableStateOf(UpdateInstallPhase.Idle)
        internal set

    var downloadedMb by mutableStateOf(0f)
        internal set

    var totalMb by mutableStateOf(0f)
        internal set

    var speedMbps by mutableStateOf(0f)
        internal set

    internal var fetchTick by mutableStateOf(0)
        internal set

    internal var installTick by mutableStateOf(0)
        internal set

    internal var autoTick by mutableStateOf(0)

    internal var timerTick by mutableStateOf(0)

    private var manualPending = false

    private var lastCheckAt = 0L

    fun requestManualCheck() {
        manualPending = true
        fetchTick++
    }

    fun onHomeEntered() {
        autoTick++
    }

    fun snooze(context: android.content.Context) {
        AppPrefs.prefs(context, CLIENT_PREFS).edit()
            .putLong("updateSnoozeUntil", System.currentTimeMillis() + 6 * 60 * 60 * 1000L)
            .apply()
        prompt = null
    }

    suspend fun autoCheck(context: android.content.Context) {
        val manual = manualPending
        manualPending = false
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheckAt < 30 * 60_000L) return
        lastCheckAt = now
        checking = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { UpdateChecker.check(context) }
        }
        checking = false
        checked = true
        val found = result.getOrNull()
        appUpdate = found
        result.exceptionOrNull()?.let { failure ->
            error = failure.message ?: failure.javaClass.simpleName
            log.w("update check failed ${failure.message}")
        }
        if (found == null) {
            if (manual && error == null) {
                Toast.makeText(context, "No new updates yet", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!manual) {
            val snoozedUntil = AppPrefs.prefs(context, CLIENT_PREFS)
                .getLong("updateSnoozeUntil", 0L)
            if (snoozedUntil > now) {
                log.i("update found but snoozed for another ${(snoozedUntil - now) / 60000}min")
                return
            }
        }
        phase = UpdateInstallPhase.Idle
        prompt = found
    }

    suspend fun install(context: android.content.Context) {
        val target = prompt ?: appUpdate ?: return
        prompt = target

        // If the APK is already cached and valid, skip download entirely and prompt installation
        val cached = withContext(Dispatchers.IO) { UpdateInstaller.cachedApk(context, target) }
        val file = if (cached != null) {
            log.i("Using already downloaded update APK: ${cached.name}")
            cached
        } else {
            phase = UpdateInstallPhase.Downloading
            downloadedMb = 0f
            totalMb = 0f
            speedMbps = 0f
            val downloaded = withContext(Dispatchers.IO) {
                UpdateInstaller.download(context, target) { done, total, speed ->
                    downloadedMb = done / 1_000_000f
                    totalMb = total / 1_000_000f
                    speedMbps = speed / 1_000_000f
                }
            }
            if (downloaded == null) {
                phase = UpdateInstallPhase.Idle
                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                return
            }
            downloaded
        }

        phase = UpdateInstallPhase.Installing
        runCatching {
            UpdateInstaller.install(context, file) { ok, message ->
                phase = UpdateInstallPhase.Idle
                if (!ok) {
                    log.w("install failed $message")
                    // If the user was just redirected to Settings to enable unknown app installs,
                    // don't dismiss the prompt or show an error toast—let them return and install.
                    if (message != UpdateInstaller.ERR_PERMISSION_REQUIRED) {
                        prompt = null
                        Toast.makeText(context, "Install failed: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.onFailure {
            phase = UpdateInstallPhase.Idle
            Toast.makeText(context, "Install failed", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun rememberUpdateController(): UpdateController = remember { UpdateController() }

@Composable
internal fun UpdateEffects(controller: UpdateController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val targetVersion = controller.prompt?.version ?: controller.appUpdate?.version
        withContext(Dispatchers.IO) { UpdateInstaller.cleanup(context, keepVersion = targetVersion) }
        controller.onHomeEntered()
        while (isActive) {
            delay(30 * 60 * 1000L)
            controller.timerTick++
        }
    }
    LaunchedEffect(controller.autoTick, controller.timerTick, controller.fetchTick) {
        if (controller.autoTick == 0 && controller.timerTick == 0 && controller.fetchTick == 0) {
            return@LaunchedEffect
        }
        controller.autoCheck(context)
    }
    LaunchedEffect(controller.installTick) {
        if (controller.installTick == 0) return@LaunchedEffect
        controller.install(context)
    }
    var dialogVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(controller.prompt) {
        if (controller.prompt == null) {
            dialogVisible = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(1500)
        dialogVisible = true
    }
    if (dialogVisible && controller.prompt != null) {
        UpdateDialog(
            update = controller.prompt!!,
            currentVersion = UpdateChecker.currentVersion(context).orEmpty(),
            phase = controller.phase,
            downloadedMb = controller.downloadedMb,
            totalMb = controller.totalMb,
            speedMbps = controller.speedMbps,
            onLater = { controller.snooze(context) },
            onInstall = { controller.installTick++ },
            onDismiss = { controller.prompt = null },
        )
    }
}
