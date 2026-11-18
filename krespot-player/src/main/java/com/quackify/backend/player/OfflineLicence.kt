/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (into whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.player

import android.content.Context
import android.media.MediaDrm
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private val log = Logs.of(Logs.Module.DRM)

internal object OfflineLicence {

    private const val DISABLE_AFTER = 2

    private const val DEFAULT_TTL_MS = 6 * 60 * 60_000L

    private const val MAX_TTL_MS = 30L * 24 * 60 * 60_000L

    @Volatile
    private var refusals = 0

    fun disabled(context: Context): Boolean =
        refusals >= DISABLE_AFTER || LicenseStore.offlineUnsupported(context)

    private fun refused(context: Context, reason: String) {
        refusals++
        log.w("offline licence refused ($refusals/$DISABLE_AFTER): $reason, streaming online instead")
        if (refusals >= DISABLE_AFTER) {
            LicenseStore.markOfflineUnsupported(context)
            log.w("offline licences look unsupported for this client, not asking again this week")
        }
    }

    /**
     * The online streaming licence itself says persist=0, i.e. this client is
     * only ever handed streaming licences: paying an extra offline attempt
     * per track would just burn a 400, so stand down until the weekly retry.
     * Observed live: every offline request 400s with an empty body while the
     * streaming licence returns 200 with persist=0.
     */
    fun noteStreamingOnly(context: Context) {
        if (LicenseStore.offlineUnsupported(context)) return
        LicenseStore.markOfflineUnsupported(context)
        log.w("licence policy says persist=0, offline caching disabled for this client (weekly retry)")
    }

    /**
     * Returns the keySetId (already persisted via LicenseStore), or null when
     * offline licences are unavailable and the caller should stream online as
     * before. Throws only when the endpoint rate-limited us, so the caller
     * surfaces the same DRM_LICENSE_ACQUISITION_FAILED refusal the online
     * path would.
     */
    suspend fun acquire(context: Context, source: AudioSource): ByteArray? {
        if (disabled(context)) return null
        val pssh = runCatching { Base64.decode(source.seektable.pssh, Base64.DEFAULT) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return withContext(Dispatchers.IO) {
            val drm = runCatching { DrmLevel.provider().acquireExoMediaDrm(C.WIDEVINE_UUID) }
                .getOrNull()
                ?: return@withContext null
            try {
                val session = drm.openSession()
                try {
                    val request = runCatching {
                        val initData = listOf(DrmInitData.SchemeData(C.WIDEVINE_UUID, "cenc", pssh))
                        drm.getKeyRequest(session, initData, MediaDrm.KEY_TYPE_OFFLINE, null)
                    }.getOrNull()
                    if (request == null) {
                        refused(context, "the cdm rejected the pssh")
                        return@withContext null
                    }
                    val response = Net.postBlob(
                        LicenseEndpoint.of(context),
                        request.data,
                        null,
                        PlayerHeaders.license(context),
                        tag = "audio-license-offline",
                    )
                    val body = response?.takeIf { it.ok }
                    if (body == null) {
                        if (response?.code == 429) {
                            LicenseEndpoint.invalidate(context)
                            LicenseGate.hold(response.code, (response.retryAfterSeconds ?: 0L) * 1000L)
                            throw IOException("audio licence 429 (DRM_LICENSE_ACQUISITION_FAILED) offline")
                        }
                        when {
                            response == null ->
                                log.w("offline licence network failed, streaming online instead")
                            // A 4xx means the endpoint rejected the request
                            // itself (offline type = 400 on this endpoint),
                            // which is stable behaviour, not a hiccup.
                            response.code in 400..499 -> refused(context, "http=${response.code}")
                            else ->
                                log.w("offline licence http=${response.code}, streaming online instead")
                        }
                        return@withContext null
                    }
                    val keySetId = runCatching { drm.provideKeyResponse(session, body.bytes) }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                    if (keySetId == null) {
                        refused(context, "the licence carries no key set")
                        return@withContext null
                    }
                    val policy = LicencePolicy.policyOf(body.bytes)
                    if (policy != null && policy[2] == 0L) {
                        refused(context, "the policy forbids persistence (persist=0)")
                        return@withContext null
                    }
                    LicenseGate.clear()
                    val ttl = policy?.get(6)?.takeIf { it > 0L }?.times(1000L)
                        ?.coerceAtMost(MAX_TTL_MS) ?: DEFAULT_TTL_MS
                    LicenseStore.save(context, source.fileId, keySetId, ttl)
                    log.i(
                        "offline licence for '${source.fileId.take(12)}' keySet=${keySetId.size}B " +
                            "ttl=${ttl / 60_000}min ${LicencePolicy.describe(body.bytes)}"
                    )
                    keySetId
                } finally {
                    runCatching { drm.closeSession(session) }
                }
            } finally {
                runCatching { drm.release() }
            }
        }
    }
}