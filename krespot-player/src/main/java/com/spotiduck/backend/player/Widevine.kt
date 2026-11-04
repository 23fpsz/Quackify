package com.spotiduck.backend.player

import android.content.Context
import android.media.MediaCrypto
import android.media.MediaDrm
import android.media.NotProvisionedException
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.core.net.Net

private val log = Logs.of(Logs.Module.DRM)

private val WIDEVINE: UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

private const val KEY_LICENSE_DURATION = "LicenseDurationRemaining"
private const val KEY_PLAY_ALLOWED = "PlayAllowed"
private const val KEY_MIME = "audio/mp4"

private const val LICENSE_HOLD_MS = 0L

private const val LICENSE_REUSE_FLOOR_S = 60L

private const val LICENSE_KEEP_FRACTION = 10L

private const val LICENSE_MAX_WAIT_MS = 120_000L


private const val MAX_SLOTS = 12

private const val PROPERTY_PRIVACY_MODE = "privacyMode"
private const val PROPERTY_SESSION_SHARING = "sessionSharing"
private const val ENABLE = "enable"

private const val TYPE_CERTIFICATE_REQUEST = 4

internal class Widevine(
    private val context: Context,
    private val spclient: () -> String,
) {

    private class Slot(
        val pssh: String,
        val session: ByteArray,
        val crypto: MediaCrypto,
        val issuedAt: Long,
        val seconds: Long,
    )

    private val gate = Mutex()

    private var drm: MediaDrm? = null
    private var prepared = false
    private var cachedCertificate: ByteArray? = null
    private var current: Slot? = null
    private val slots = LinkedHashMap<String, Slot>()
    private var lastAskAt = 0L

    @Volatile
    var reason: String = ""
        private set

    val mediaCrypto: MediaCrypto? get() = current?.crypto

    val remainingSeconds: Long
        get() {
            val slot = current ?: return 0L
            if (slot.seconds <= 0L) return 0L
            val elapsed = (SystemClock.elapsedRealtime() - slot.issuedAt) / 1000L
            return (slot.seconds - elapsed).coerceAtLeast(0L)
        }

    suspend fun open(pssh: String): MediaCrypto? = gate.withLock {
        reason = ""
        slots[pssh]?.let { kept ->
            val left = aliveSeconds(kept)
            if (left > keepFloor(kept)) {
                current = kept
                log.i("licence for this pssh is good for another ${left}s, nothing asked")
                return@withLock kept.crypto
            }
        }
        val closed = LicenseGate.remainingMs()
        if (closed > 0L) {
            fail("licence gate ${(closed + 999L) / 1000L}s")
            return@withLock null
        }
        val instance = drm ?: runCatching { MediaDrm(WIDEVINE) }.getOrElse {
            fail("MediaDrm unavailable ${it.javaClass.simpleName}: ${it.message}")
            return@withLock null
        }
        drm = instance
        if (!prepared) {
            prepared = true
            prepare(instance)
        }
        log.i("drm securityLevel=${level(instance)} " +
                "version=${runCatching { instance.getPropertyString("version") }.getOrNull()}"
        )
        trim(instance)
        val slot = license(instance, pssh) ?: return@withLock null
        current = slot
        slots[pssh] = slot
        slot.crypto
    }

    suspend fun refresh(): MediaCrypto? = gate.withLock {
        // A successful refresh clears the license gate, which would also kill
        // the pending retry of a track the gate refused; while it is closed,
        // keep playing on the session that is already working.
        if (LicenseGate.remainingMs() > 0L) return@withLock null
        val slot = current ?: return@withLock null
        val instance = drm ?: return@withLock null
        val fresh = license(instance, slot.pssh) ?: return@withLock null
        slots.remove(slot.pssh)
        slots[fresh.pssh] = fresh
        current = fresh
        runCatching { instance.closeSession(slot.session) }
        fresh.crypto
    }

    suspend fun close() = gate.withLock {
        val instance = drm
        slots.values.forEach { runCatching { instance?.closeSession(it.session) } }
        slots.clear()
        current = null
        runCatching { instance?.release() }
        drm = null
        prepared = false
        reason = ""
    }

    private fun aliveSeconds(slot: Slot): Long {
        if (slot.seconds <= 0L) return 0L
        val elapsed = (SystemClock.elapsedRealtime() - slot.issuedAt) / 1000L
        return (slot.seconds - elapsed).coerceAtLeast(0L)
    }

    private fun keepFloor(slot: Slot): Long =
        maxOf(LICENSE_REUSE_FLOOR_S, slot.seconds / LICENSE_KEEP_FRACTION)

    private fun trim(instance: MediaDrm) {
        val dropped = slots.entries.filter { (_, slot) -> aliveSeconds(slot) <= keepFloor(slot) }
        dropped.forEach { (key, slot) ->
            slots.remove(key)
            if (current === slot) current = null
            runCatching { instance.closeSession(slot.session) }
        }
        while (slots.size >= MAX_SLOTS) {
            val oldest = slots.entries.minByOrNull { it.value.issuedAt } ?: break
            slots.remove(oldest.key)
            if (current === oldest.value) current = null
            runCatching { instance.closeSession(oldest.value.session) }
            log.i("licence session for ${oldest.key.take(8)} closed, ${slots.size} kept")
        }
    }

    private fun prepare(instance: MediaDrm) {
        for (name in listOf(PROPERTY_PRIVACY_MODE, PROPERTY_SESSION_SHARING)) {
            val outcome = runCatching { instance.setPropertyString(name, ENABLE) }
            log.i("drm $name=$ENABLE " +
                    if (outcome.isSuccess) "accepted" else "refused ${outcome.exceptionOrNull()?.message}"
            )
        }
    }

    private fun open(instance: MediaDrm): ByteArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            instance.openSession(MediaDrm.SECURITY_LEVEL_SW_SECURE_CRYPTO)
        } else {
            instance.openSession()
        }

    private fun sessionLevel(instance: MediaDrm, session: ByteArray): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { instance.getSecurityLevel(session).toString() }.getOrNull() ?: "?"
        } else {
            "?"
        }

    private suspend fun openSession(instance: MediaDrm): ByteArray? = try {
        open(instance)
    } catch (missing: NotProvisionedException) {
        provision(instance)
    } catch (failure: Throwable) {
        fail("openSession ${failure.javaClass.simpleName}: ${failure.message}")
        null
    }

    private suspend fun provision(instance: MediaDrm): ByteArray? {
        val request = runCatching { instance.getProvisionRequest() }.getOrNull()
        if (request == null) {
            fail("provision: no request")
            return null
        }
        val signed = String(request.data, Charsets.UTF_8)
        val separator = if (request.defaultUrl.contains("?")) "&" else "?"
        val url = request.defaultUrl + separator + "signedRequest=" + URLEncoder.encode(signed, "UTF-8")
        val response = Net.postBlob(url, ByteArray(0), "application/octet-stream", tag = "provision")
        val bytes = response?.takeIf { it.ok }?.bytes
        if (bytes == null) {
            fail("provision failed ${response?.code ?: "no response"}")
            return null
        }
        return runCatching {
            instance.provideProvisionResponse(bytes)
            log.i("provisioned ${bytes.size}B")
            open(instance)
        }.getOrElse {
            fail("provideProvisionResponse ${it.javaClass.simpleName}: ${it.message}")
            null
        }
    }

    private fun level(instance: MediaDrm): String =
        runCatching { instance.getPropertyString("securityLevel") }.getOrNull() ?: "?"

    private fun licenseKeyIds(body: ByteArray): String {
        val out = StringBuilder()
        var index = 0
        while (index + 18 <= body.size) {
            if ((body[index].toInt() and 0xff) == 0x0A && (body[index + 1].toInt() and 0xff) == 0x10) {
                if (out.isNotEmpty()) out.append(',')
                out.append(body.copyOfRange(index + 2, index + 18).joinToString("") { "%02x".format(it) })
                index += 18
            } else {
                index++
            }
        }
        return out.ifEmpty { "none" }.toString()
    }

    private fun topLevel(data: ByteArray): String {
        val out = StringBuilder()
        var i = 0
        while (i < data.size) {
            val tag = data[i++].toInt() and 0xff
            out.append(tag ushr 3).append(' ')
            when (tag and 7) {
                0 -> {
                    while (i < data.size && (data[i].toInt() and 0x80) != 0) i++
                    i++
                }
                1 -> i += 8
                2 -> {
                    var length = 0
                    var shift = 0
                    while (i < data.size) {
                        val byte = data[i++].toInt() and 0xff
                        length = length or ((byte and 0x7f) shl shift)
                        shift += 7
                        if (byte and 0x80 == 0) break
                    }
                    i += length
                }
                5 -> i += 4
                else -> return "$out?"
            }
            if (i > data.size) return "$out!"
        }
        return out.toString().trim()
    }

    private suspend fun license(
        instance: MediaDrm,
        pssh: String,
    ): Slot? {
        val scope = runCatching { Base64.decode(pssh, Base64.DEFAULT) }.getOrNull()
        if (scope == null) {
            fail("pssh is not base64")
            return null
        }
        var session = openSession(instance) ?: return null
        log.i("drm session level=${sessionLevel(instance, session)}")
        var request = keyRequest(instance, session, scope)
        if (request != null && signedType(request.data) == TYPE_CERTIFICATE_REQUEST) {
            log.i("the cdm asked for a service certificate before a licence " +
                    "(${request.data.size}B), answering it locally"
            )
            val certified = try {
                certify(instance)
            } catch (cancellation: CancellationException) {
                runCatching { instance.closeSession(session) }
                throw cancellation
            }
            if (certified) {
                runCatching { instance.closeSession(session) }
                session = openSession(instance) ?: return null
                log.i("drm session level=${sessionLevel(instance, session)} after the certificate")
                request = keyRequest(instance, session, scope)
            }
        }
        if (request == null) {
            runCatching { instance.closeSession(session) }
            return null
        }
        if (signedType(request.data) == TYPE_CERTIFICATE_REQUEST) {
            runCatching { instance.closeSession(session) }
            fail("the cdm still wants a certificate with one applied")
            return null
        }
        val asked = SystemClock.elapsedRealtime()
        val gap = if (lastAskAt == 0L) -1L else (asked - lastAskAt) / 1000L
        lastAskAt = asked
        log.i("key request ${request.data.size}B pssh=${scope.size}B " +
                "kids=${countKids(pssh)} gap=${gap}s " +
                "fields=${topLevel(request.data)}"
        )
        val response = try {
            postLicense(request.data)
        } catch (cancellation: CancellationException) {
            // A skip cancels the play job mid-POST; without this the session
            // opened above stays open and untracked until the CDM fills up.
            runCatching { instance.closeSession(session) }
            throw cancellation
        }
        if (response != null && response.ok) {
            val slot = provided(instance, session, pssh, response.bytes)
            if (slot != null) return slot
            runCatching { instance.closeSession(session) }
            return null
        }
        runCatching { instance.closeSession(session) }
        val code = response?.code ?: -1
        if (throttled(code)) {
            log.w("license $code ${response?.size ?: 0}B headers=${response?.headers.orEmpty()}")
        }
        LicenseGate.hold(retryAfterMs(response) ?: LICENSE_HOLD_MS)
        fail("license $code ${response?.size ?: 0}B")
        return null
    }

    private fun countKids(pssh: String): Int {
        val raw = runCatching { Base64.decode(pssh, Base64.DEFAULT) }.getOrNull() ?: return 0
        if (raw.size < 32) return 0
        val payloadSize = be32(raw, 28)
        if (payloadSize <= 0 || 32 + payloadSize > raw.size) return 0
        val payload = raw.copyOfRange(32, 32 + payloadSize)
        var count = 0
        var i = 0
        while (i + 18 <= payload.size) {
            if ((payload[i].toInt() and 0xff) == 0x12 && (payload[i + 1].toInt() and 0xff) == 0x10) {
                count++
                i += 18
            } else {
                i++
            }
        }
        return count
    }

    private fun be32(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0xff) shl 24) or
                ((data[at + 1].toInt() and 0xff) shl 16) or
                ((data[at + 2].toInt() and 0xff) shl 8) or
                (data[at + 3].toInt() and 0xff)

    private fun keyRequest(
        instance: MediaDrm,
        session: ByteArray,
        scope: ByteArray,
    ): MediaDrm.KeyRequest? = runCatching {
        instance.getKeyRequest(session, scope, KEY_MIME, MediaDrm.KEY_TYPE_STREAMING, null)
    }.getOrElse {
        fail("getKeyRequest ${it.javaClass.simpleName}: ${it.message}")
        null
    }

    private fun signedType(data: ByteArray): Int {
        if (data.isEmpty()) return -1
        val tag = data[0].toInt() and 0xff
        if (tag ushr 3 != 1) return -1
        if (tag and 7 != 0) return -1
        if (data.size < 2) return -1
        return data[1].toInt() and 0xff
    }

    private suspend fun certify(instance: MediaDrm): Boolean {
        cachedCertificate?.let { return it.isNotEmpty() }
        val host = spclient().ifBlank { "guc3-spclient.spotify.com" }
        val url = "https://$host/widevine-license/v1/application-certificate"
        val response = Net.blob(url, PlayerHeaders.of(context), tag = "certificate")
        val bytes = response?.takeIf { it.ok }?.bytes
        if (bytes == null || bytes.isEmpty()) {
            log.w("application certificate ${response?.code ?: "no response"}, the challenge stays unsigned")
            return false
        }
        val applied = runCatching {
            instance.setPropertyByteArray("serviceCertificate", bytes)
        }
        log.i("application certificate ${bytes.size}B ${response.code} " +
                if (applied.isSuccess) "applied to the cdm" else "refused ${applied.exceptionOrNull()?.message}"
        )
        cachedCertificate = if (applied.isSuccess) bytes else null
        return applied.isSuccess
    }

    private fun provided(
        instance: MediaDrm,
        session: ByteArray,
        pssh: String,
        body: ByteArray,
    ): Slot? {
        val keys = runCatching { instance.provideKeyResponse(session, body) }.getOrElse {
            fail("provideKeyResponse ${it.javaClass.simpleName}: ${it.message}")
            return null
        }
        val created = runCatching { MediaCrypto(WIDEVINE, session) }.getOrNull()
        if (created == null) {
            fail("MediaCrypto rejected the session")
            return null
        }
        val status = runCatching { instance.queryKeyStatus(session) }.getOrDefault(emptyMap())
        val ttl = status[KEY_LICENSE_DURATION]?.toLongOrNull() ?: 0L
        LicenseGate.clear()
        log.i("license ${body.size}B keys=${keys?.size ?: 0} " +
                "play=${status[KEY_PLAY_ALLOWED]} ttl=${ttl}s slots=${slots.size} " +
                "ids=${licenseKeyIds(body)}"
        )
        log.d("license held=${status.entries.joinToString(",") { "${it.key.take(40)}=${it.value.take(40)}" }}")
        if (status[KEY_PLAY_ALLOWED]?.equals("true", ignoreCase = true) == false) {
            fail("license says play=${status[KEY_PLAY_ALLOWED]}")
            return null
        }
        return Slot(pssh, session, created, SystemClock.elapsedRealtime(), ttl)
    }

    private suspend fun postLicense(data: ByteArray): Net.Blob? {
        val headers = PlayerHeaders.license(context)
        val age = SpotifyAuth.tokenAgeMs(context)
        log.i("license post ${data.size}B bearer=${SpotifyAuth.token(context).take(12)}… " +
                "token=${if (age >= 0L) "${age / 1000}s" else "?"} old, " +
                headers.keys.joinToString(",")
        )
        return Net.postBlob(
            LicenseEndpoint.of(context, spclient()),
            data,
            "application/octet-stream",
            headers,
            tag = "license",
        )
    }

    private fun retryAfterMs(response: Net.Blob?): Long? {
        val seconds = response?.retryAfterSeconds ?: return null
        return (seconds * 1000L).coerceIn(1_000L, LICENSE_MAX_WAIT_MS)
    }

    private fun fail(message: String): Boolean {
        reason = message
        log.e(message)
        return false
    }

    private fun throttled(code: Int?): Boolean = code == 429 || code == 503
}
