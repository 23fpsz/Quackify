package com.quackify.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.quackify.backend.core.net.ConnectivityMonitor

/**
 * Shared backing state for every offline banner in the shell. It is written by
 * the process-wide [ConnectivityMonitor] and read by all [rememberIsOffline]
 * call sites, so the verdict survives any amount of recomposition — a screen
 * coming and going, the mini player loading, the bottom bar being swapped —
 * without the listener ever being dropped (the old per-composable callback
 * silently stopped updating once the mini player loaded, which is how the
 * "no offline banner" bug happened).
 *
 * The subscription is wired once per process and never unwired: the lambda
 * only touches this file-level state, so there is nothing to leak, and the
 * state can never go stale between screens.
 */
private val offlineState = mutableStateOf(ConnectivityMonitor.isOffline())

private var permanentListener: ((Boolean) -> Unit)? = null

@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        if (permanentListener == null) {
            ConnectivityMonitor.ensureStarted(context.applicationContext)
            val listener: (Boolean) -> Unit = { offline -> offlineState.value = offline }
            permanentListener = listener
            ConnectivityMonitor.addListener(listener)
        }
        // Fresh read on every (re)composition site in case the banner was
        // not on screen while the verdict last flipped.
        offlineState.value = ConnectivityMonitor.isOffline()
        onDispose { }
    }
    return offlineState.value
}
