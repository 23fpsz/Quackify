package com.quackify.app

import android.content.Context
import com.quackify.backend.connect.cluster.Device
import com.quackify.backend.connect.command.Registration
import com.quackify.backend.connect.dealer.DealerHost
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.auth.WebToken
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ClientToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Signs a removed account out on Spotify's side, not just on this device:
 * the session cookies are revoked through the web player's logout endpoint
 * and this device's entries are dropped from that account's connect cluster.
 *
 * Everything the network work needs is captured synchronously in [begin] —
 * the stored snapshot disappears the moment the caller moves on to the local
 * removal — while the calls themselves run on a process-wide scope, so they
 * survive the activity recreate that follows an active-account removal.
 * Failures only cost a lingering server-side entry; they never block the
 * local removal.
 */
object AccountSignOut {

    private val log = Logs.of(Logs.Module.AUTH)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun begin(context: Context, accountId: String) {
        val app = context.applicationContext
        val session = EncoreAuth.accountSession(app, accountId)
        val wasActive = EncoreAuth.activeAccountId(app) == accountId
        // Captured now: the local removal below may wipe or swap the active
        // token before the coroutine gets to read it.
        val activeToken = if (wasActive) EncoreAuth.token(app) else ""
        if (session.isBlank() && activeToken.isBlank()) {
            log.w("sign-out of '${accountId.take(8)}' skipped, nothing saved to act on")
            return
        }
        log.i("signing '${accountId.take(8)}' out on the server (active=$wasActive)")
        scope.launch {
            runCatching { signOut(app, session, activeToken) }
                .onFailure { log.w("sign-out failed ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private suspend fun signOut(context: Context, session: String, activeToken: String) {
        // A token for the outgoing account: the active one while it lasts,
        // otherwise one minted from the account's own sp_dc — minted before
        // the session is revoked, because a revoked sp_dc mints nothing.
        val token = activeToken.ifBlank {
            WebToken.peek(context, EncoreAuth.spDcOf(session))?.first.orEmpty()
        }
        val cookies = EncoreAuth.openSessionCookies(session)
        val spclient = DealerHost.resolve(context)?.second.orEmpty()

        val revoked = EncoreAuth.revokeRemoteSession(cookies)

        val devices = if (spclient.isNotBlank() && token.isNotBlank()) {
            Registration.signOutDevices(spclient, Device.id(context), token, ClientToken.get(context))
        } else {
            log.w("device sign-out skipped, token=${token.isNotBlank()} spclient=${spclient.isNotBlank()}")
            0
        }
        log.i("sign-out finished revoked=$revoked devicesRemoved=$devices")
    }
}
