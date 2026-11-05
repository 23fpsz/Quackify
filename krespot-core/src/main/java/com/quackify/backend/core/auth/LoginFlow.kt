package com.quackify.backend.core.auth

import android.content.Context
import android.os.SystemClock
import com.quackify.backend.core.net.Pathfinder
import kotlinx.coroutines.delay
import com.quackify.backend.core.log.Logs

private val log = Logs.of(Logs.Module.LOGIN_FLOW)

object LoginFlow {

    fun ops(): Int = Pathfinder.count()

    fun markAttempt(context: Context) {
        Pathfinder.markAttempt(context)
    }

    suspend fun harvest(
        context: Context,
        log: (String) -> Unit,
        timeoutMs: Long = 20_000,
        requiredOp: String = "home",
        ready: () -> Boolean = { true },
    ): Boolean {
        Pathfinder.load(context)
        val done = { Pathfinder.hashOf(requiredOp) != null && ready() }
        if (done()) {
            log("pathfinder cached (${Pathfinder.count()} ops, '$requiredOp' + credentials ready)")
            return true
        }
        log("harvesting until '$requiredOp' and credentials, ${timeoutMs}ms cap")
        val started = SystemClock.elapsedRealtime()
        var lastLogAt = 0L
        while (SystemClock.elapsedRealtime() - started < timeoutMs) {
            if (done()) break
            val elapsed = SystemClock.elapsedRealtime() - started
            if (elapsed - lastLogAt >= 3000) {
                lastLogAt = elapsed
                log(
                    "harvest ${elapsed}ms ops=${Pathfinder.count()} " +
                        "token=${SpotifyAuth.token(context).isNotBlank()}",
                )
            }
            delay(250)
        }
        val ok = done()
        log(
            "harvest finished ops=${Pathfinder.count()} ok=$ok " +
                "in ${SystemClock.elapsedRealtime() - started}ms",
        )
        return ok
    }
}
