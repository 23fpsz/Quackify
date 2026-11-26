package com.quackify.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quackify.backend.api.FriendActivity
import com.quackify.backend.core.log.Logs
import com.quackify.backend.model.FriendActivityEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val log = Logs.of(Logs.Module.MAIN)

class FriendActivityFeedController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var feed by mutableStateOf<List<FriendActivityEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var fetchTick by mutableStateOf(0)
        private set

    fun open() {
        fetchTick++
    }

    suspend fun load() {
        loading = true
        failed = false
        val result = runCatching { FriendActivity.feed(context) }.getOrNull()
        if (result != null) {
            feed = result.entries
        }
        failed = result == null
        loading = false
        log.i("friend feed loaded items=${result?.entries?.size ?: 0} failed=${result == null}")
    }

    fun refreshSilently() {
        scope.launch {
            val result = runCatching { FriendActivity.feed(context) }.getOrNull() ?: return@launch
            feed = result.entries
            log.i("friend feed refreshed items=${result.entries.size}")
        }
    }
}
