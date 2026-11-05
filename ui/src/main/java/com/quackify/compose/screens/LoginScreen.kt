package com.quackify.compose.screens

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.backend.core.auth.LoginFlow
import com.quackify.compose.theme.EncoreColors
import com.quackify.backend.core.webview.LOGIN_URL
import com.quackify.backend.core.webview.LoginWebViewHost
import com.quackify.backend.core.webview.PLAYER_URL
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import com.quackify.backend.core.log.Logs

private val log = Logs.of(Logs.Module.LOGIN)

private sealed interface Resolve {
    data class Ok(val account: SpotifyAuth.Account) : Resolve
    data object Guest : Resolve
    data object NotReady : Resolve
}

/**
 * Full-screen Spotify login webview. Polls for a signed-in session, resolves
 * the account behind it and hands it to [onLoggedIn].
 *
 * When signing into an *additional* account, pass [isDuplicateAccount]: the
 * moment the resolved account matches one already saved on this device the
 * login is refused and [onDuplicateLogin] fires instead — the same account
 * cannot be added twice.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    isDuplicateAccount: ((SpotifyAuth.Account) -> Boolean)? = null,
    onDuplicateLogin: ((SpotifyAuth.Account) -> Unit)? = null,
) {

    val context = LocalContext.current
    val host = remember { LoginWebViewHost(context) }
    var splash by remember { mutableStateOf(false) }
    val navSignal = remember { Channel<Unit>(Channel.CONFLATED) }
    val webAlpha by animateFloatAsState(
        targetValue = if (splash) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (splash) 200 else 320,
            easing = if (splash) FastOutLinearInEasing else LinearOutSlowInEasing,
        ),
        label = "webviewAlpha",
    )

    DisposableEffect(Unit) {
        host.onPlayerReached = { navSignal.trySend(Unit) }
        onDispose {
            host.onPlayerReached = null
            host.destroy()
        }
    }

    LaunchedEffect(Unit) {
        log.i("poll loop started")
        var first = true
        var busy = false
        var lastSpDc = ""
        var lastTryAt = 0L
        var rescues = 0
        while (isActive) {
            if (!first) withTimeoutOrNull(1500) { navSignal.receive() }
            first = false
            if (busy) continue
            if (!host.isOnPlayer) {
                if (splash) log.i("left the player, hiding splash")
                splash = false
                continue
            }
            val cookies = SpotifyAuth.captureCookies(context)
            val spDc = cookies?.let { SpotifyAuth.spDcOf(it) }.orEmpty()
            if (spDc.isBlank()) continue

            val now = SystemClock.elapsedRealtime()
            if (spDc == lastSpDc && now - lastTryAt < 4000) continue
            lastSpDc = spDc
            lastTryAt = now

            log.i("resolving url=${host.webView.url} sp_dc len=${spDc.length}")
            busy = true
            splash = true
            val started = SystemClock.elapsedRealtime()
            val result = withTimeoutOrNull(30_000) {
                resolveAccount(host, spDc) { log.i(it) }
            } ?: Resolve.NotReady
            busy = false

            when (result) {
                is Resolve.Ok -> {
                    log.i("resolved '${result.account.name}' in ${SystemClock.elapsedRealtime() - started}ms")
                    if (isDuplicateAccount != null && isDuplicateAccount(result.account)) {
                        log.w("login refused: '${result.account.name}' (${result.account.id}) is already saved here")
                        onDuplicateLogin?.invoke(result.account)
                        return@LaunchedEffect
                    }
                    SpotifyAuth.saveLogin(context, cookies!!, result.account)
                    val userId = SpotifyAuth.userId(context)
                    log.i("account id resolved='$userId'")
                    LoginFlow.harvest(context, log = { log.i(it) })
                    onLoggedIn()
                    return@LaunchedEffect
                }

                Resolve.Guest -> {
                    log.w("guest session on the player, reopening login")
                    splash = false
                    host.load(LOGIN_URL)
                }

                Resolve.NotReady -> {
                    log.w("not ready (${SystemClock.elapsedRealtime() - started}ms)")
                    if (rescues < 2 && ++rescues == 2) {
                        log.w("giving up on the player, reopening login")
                        splash = false
                        host.load(LOGIN_URL)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .alpha(webAlpha),
            factory = { host.container },
        )

        AnimatedVisibility(
            visible = splash,
            enter = fadeIn(
                tween(durationMillis = 340, easing = LinearOutSlowInEasing),
            ) + scaleIn(
                animationSpec = tween(durationMillis = 340, easing = LinearOutSlowInEasing),
                initialScale = 0.94f,
            ),
            exit = fadeOut(
                tween(durationMillis = 300, easing = FastOutLinearInEasing),
            ) + scaleOut(
                animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing),
                targetScale = 1.06f,
            ),
        ) {
            SplashScreen()
        }
    }
}

private suspend fun resolveAccount(
    host: LoginWebViewHost,
    spDc: String,
    log: (String) -> Unit,
): Resolve {
    if (!host.isOnPlayer) {
        log("not on the player yet (url=${host.webView.url})")
        return Resolve.NotReady
    }

    val session = host.readSession()
    val token = session?.token
    log(if (token != null) "session token ok (${token.length})" else "no session token")
    if (session != null) {
        SpotifyAuth.saveToken(host.context, session.token, session.expiresAtMs)
        val account = SpotifyAuth.fetchAccount(session.token)
        log(if (account != null) "/v1/me ok (${account.name})" else "/v1/me failed")
        if (account != null && account.name.isNotBlank()) return Resolve.Ok(account)
    }

    log("reading the user widget")
    val user = host.readUser()
    if (user != null) {
        if (user.isGuest) {
            log("user widget says guest")
            return Resolve.Guest
        }
        log("user widget ok (${user.name})")
        return Resolve.Ok(SpotifyAuth.Account(name = user.name, imageUrl = user.avatarUrl))
    }
    log("user widget not found")

    log("token over http")
    val httpToken = SpotifyAuth.fetchToken(host.context, spDc)
    log(if (httpToken != null) "http token ok (${httpToken.length})" else "http token: none")
    if (httpToken != null) {
        SpotifyAuth.fetchAccount(httpToken)?.let { account ->
            if (account.name.isNotBlank()) return Resolve.Ok(account)
        }
    }
    return Resolve.NotReady
}
