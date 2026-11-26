package com.quackify.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.quackify.backend.core.log.Logs
import com.quackify.compose.screens.AppUpdate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {
    const val ERR_PERMISSION_REQUIRED = "Unknown app install permission required"
    private val log = Logs.of(Logs.Module.MAIN)
    private const val ACTION_INSTALL_STATUS = "com.quackify.app.INSTALL_STATUS"

    fun flavorOf(packageName: String): String = when {
        packageName.startsWith("com.quackify.app") -> "standard"
        packageName.startsWith("com.spotify.music") -> "spotify"
        packageName.startsWith("com.apple.android.music") -> "apple"
        else -> "standard"
    }

    fun cachedApk(context: Context, update: AppUpdate): File? {
        val file = File(context.cacheDir, "update-${update.version}.apk")
        return if (file.exists() && file.isFile && file.length() > 1_000_000L) file else null
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                android.widget.Toast.makeText(
                    context,
                    "Please allow installing unknown apps to complete the update",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        }
    }

    fun download(
        context: Context,
        update: AppUpdate,
        onProgress: (doneBytes: Long, totalBytes: Long, speedBps: Long) -> Unit,
    ): File? = runCatching {
        val cached = cachedApk(context, update)
        if (cached != null) {
            log.i("Update APK already downloaded: ${cached.name} (${cached.length()} bytes)")
            onProgress(cached.length(), cached.length(), 0)
            return cached
        }

        val conn = URL(update.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Quackify-UpdateChecker")
        try {
            if (conn.responseCode !in 200..299) return null
            val total = conn.contentLengthLong.let { if (it > 0) it else -1L }
            val tempFile = File(context.cacheDir, "update-${update.version}.apk.tmp")
            val target = File(context.cacheDir, "update-${update.version}.apk")
            if (tempFile.exists()) tempFile.delete()
            if (target.exists()) target.delete()

            var done = 0L
            var lastTick = System.currentTimeMillis()
            var lastBytes = 0L
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 250) {
                            onProgress(done, total, (done - lastBytes) * 1000 / (now - lastTick))
                            lastTick = now
                            lastBytes = done
                        }
                    }
                    onProgress(done, if (total > 0) total else done, 0)
                }
            }
            if (done <= 0 || !tempFile.renameTo(target)) {
                if (tempFile.exists()) tempFile.delete()
                return null
            }
            target
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    fun install(context: Context, file: File, onResult: (Boolean, String) -> Unit) {
        if (!canRequestPackageInstalls(context)) {
            log.w("Install permission not granted; prompting settings")
            openInstallPermissionSettings(context)
            onResult(false, ERR_PERMISSION_REQUIRED)
            return
        }

        // Try PackageInstaller session first
        runCatching {
            installViaPackageInstaller(context, file, onResult)
        }.onFailure { err ->
            log.w("PackageInstaller failed (${err.message}); falling back to ACTION_VIEW intent")
            installViaIntent(context, file, onResult)
        }
    }

    private fun installViaIntent(context: Context, file: File, onResult: (Boolean, String) -> Unit) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            onResult(true, "")
        }.onFailure { e ->
            log.e("ACTION_VIEW install intent failed: ${e.message}")
            onResult(false, e.message ?: "Failed to open installer")
        }
    }

    private fun installViaPackageInstaller(context: Context, file: File, onResult: (Boolean, String) -> Unit) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                try {
                    context.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                }
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                log.i("PackageInstaller status: $status, message: $message")
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> onResult(true, "")
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = runCatching {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        }.getOrNull()
                        if (confirm != null) {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(confirm) }
                                .onSuccess { onResult(true, "") }
                                .onFailure {
                                    log.w("Failed to start confirmation activity, falling back to intent")
                                    installViaIntent(context, file, onResult)
                                }
                        } else {
                            installViaIntent(context, file, onResult)
                        }
                    }
                    else -> {
                        log.w("PackageInstaller failed status=$status ($message), trying fallback")
                        installViaIntent(context, file, onResult)
                    }
                }
            }
        }
        val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, IntentFilter(ACTION_INSTALL_STATUS), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_INSTALL_STATUS))
        }
        val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
        val sender = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
        try {
            session.openWrite("quackify_update", 0, file.length()).use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            session.commit(sender.intentSender)
        } catch (t: Throwable) {
            runCatching { session.abandon() }
            runCatching { context.unregisterReceiver(receiver) }
            throw t
        }
    }

    fun cleanup(context: Context, keepVersion: String? = null) {
        val keepFile = keepVersion?.let { "update-$it.apk" }
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("update-") && (it.name.endsWith(".apk") || it.name.endsWith(".apk.tmp")) }
            ?.forEach {
                if (keepFile == null || it.name != keepFile) {
                    it.delete()
                }
            }
    }
}
