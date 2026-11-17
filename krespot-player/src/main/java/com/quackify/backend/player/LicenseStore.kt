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
import android.util.Base64
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.store.SecureStore

private val log = Logs.of(Logs.Module.DRM)

internal object LicenseStore {

    private const val KEY_SET = "offline_licence_set_"

    private const val KEY_EXP = "offline_licence_exp_"

    private const val EXPIRY_MARGIN_MS = 10 * 60_000L

    fun keySetIdFor(context: Context, fileId: String): ByteArray? {
        if (fileId.isBlank()) return null
        val prefs = SecureStore.prefs(context)
        val expiresAt = prefs.getLong(KEY_EXP + fileId, 0L)
        if (expiresAt <= System.currentTimeMillis() + EXPIRY_MARGIN_MS) {
            if (expiresAt > 0L) {
                forget(context, fileId)
                log.i("offline licence for '${fileId.take(12)}' expired, dropped")
            }
            return null
        }
        val stored = prefs.getString(KEY_SET + fileId, null) ?: return null
        return runCatching { Base64.decode(stored, Base64.DEFAULT) }.getOrNull()
    }

    fun save(context: Context, fileId: String, keySetId: ByteArray, ttlMs: Long) {
        SecureStore.prefs(context).edit()
            .putString(KEY_SET + fileId, Base64.encodeToString(keySetId, Base64.NO_WRAP))
            .putLong(KEY_EXP + fileId, System.currentTimeMillis() + ttlMs)
            .apply()
    }

    fun forget(context: Context, fileId: String) {
        SecureStore.prefs(context).edit()
            .remove(KEY_SET + fileId)
            .remove(KEY_EXP + fileId)
            .apply()
    }
}