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

/**
 * Widevine licence policy reader shared by the online and offline licensing
 * paths. The policy message nests inside a raw license response; its fields
 * are 1 play, 2 persist, 3 renew, 4 rental duration, 5 playback duration and
 * 6 license duration, the last two in seconds.
 */
internal object LicencePolicy {

    fun policyOf(raw: ByteArray, depth: Int = 0): Map<Int, Long>? {
        if (depth > 4) return null
        val values = mutableMapOf<Int, Long>()
        val nested = mutableListOf<ByteArray>()
        val reader = Reader(raw)
        while (!reader.done()) {
            val (field, wire) = reader.tag()
            when (wire) {
                0 -> values[field] = reader.varint()
                2 -> nested.add(reader.bytes())
                else -> reader.skip(wire)
            }
        }
        val looksLikePolicy = values.containsKey(1) && values.containsKey(2) &&
            values[1] in 0L..1L && (values.containsKey(5) || values.containsKey(6))
        if (looksLikePolicy) return values
        for (child in nested) {
            val found = runCatching { policyOf(child, depth + 1) }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    fun describe(bytes: ByteArray): String = runCatching {
        val policy = policyOf(bytes) ?: return@runCatching "policy not found"
        "play=${policy[1] ?: -1} persist=${policy[2] ?: -1} renew=${policy[3] ?: -1} " +
            "rental=${policy[4] ?: -1}s playbackDur=${policy[5] ?: -1}s licenseDur=${policy[6] ?: -1}s"
    }.getOrElse { "policy unreadable" }
}
