/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
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
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.player

internal data class Box(val type: String, val start: Int, val size: Int, val header: Int) {
    val body: Int get() = start + header
    val end: Int get() = start + size
}

internal data class Sample(
    val offset: Int,
    val size: Int,
    val duration: Int,
    val iv: ByteArray?,
    val subsamples: IntArray? = null,
)

internal data class Fragment(val entry: Int, val baseTime: Long, val samples: List<Sample>)

internal data class Init(
    val sampleRate: Int,
    val channels: Int,
    val asc: ByteArray,
    val kid: ByteArray,
    val ivSize: Int,
    val timescale: Int,
    val defaultDuration: Int,
    val defaultSize: Int,
    val encryptedEntry: Int,
    val priming: Long,
    val mediaDuration: Long,
    val mime: String,
) {
    val primingFrames: Long get() = priming * sampleRate / timescale

    val playableFrames: Long get() = (mediaDuration - priming) * sampleRate / timescale

    val playableMs: Long get() = playableFrames * 1000L / sampleRate

    fun frames(duration: Int): Long = duration.toLong() * sampleRate / timescale
}

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xff

internal fun ByteArray.u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

internal fun ByteArray.u24(at: Int): Int = (u8(at) shl 16) or (u8(at + 1) shl 8) or u8(at + 2)

internal fun ByteArray.u32(at: Int): Int =
    (u8(at) shl 24) or (u8(at + 1) shl 16) or (u8(at + 2) shl 8) or u8(at + 3)

internal fun ByteArray.u64(at: Int): Long = (u32(at).toLong() shl 32) or (u32(at + 4).toLong() and 0xffffffffL)

internal object Mp4 {

    fun boxes(buf: ByteArray, from: Int, to: Int): List<Box> {
        val out = ArrayList<Box>()
        var p = from
        while (p + 8 <= to) {
            var size = buf.u32(p)
            val type = String(buf, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1) {
                size = buf.u64(p + 8).toInt()
                header = 16
            }
            if (size < header || p + size > to) break
            out += Box(type, p, size, header)
            p += size
        }
        return out
    }

    fun parseInit(buf: ByteArray): Init {
        val moov = boxes(buf, 0, buf.size).firstOrNull { it.type == "moov" } ?: error("no moov")
        val traks = boxes(buf, moov.body, moov.end).filter { it.type == "trak" }
        val trak = traks.firstOrNull { buf.isSound(it) } ?: traks.firstOrNull() ?: error("no trak")
        val mdia = boxes(buf, trak.body, trak.end).firstOrNull { it.type == "mdia" } ?: error("no mdia")
        val mdhd = boxes(buf, mdia.body, mdia.end).firstOrNull { it.type == "mdhd" } ?: error("no mdhd")
        val minf = boxes(buf, mdia.body, mdia.end).firstOrNull { it.type == "minf" } ?: error("no minf")
        val stbl = boxes(buf, minf.body, minf.end).firstOrNull { it.type == "stbl" } ?: error("no stbl")
        val stsd = boxes(buf, stbl.body, stbl.end).firstOrNull { it.type == "stsd" } ?: error("no stsd")

        val timescale = if (buf.u8(mdhd.body) == 1) buf.u32(mdhd.body + 20) else buf.u32(mdhd.body + 12)

        val edts = boxes(buf, trak.body, trak.end).firstOrNull { it.type == "edts" }
        val elst = edts?.let { boxes(buf, it.body, it.end).firstOrNull { box -> box.type == "elst" } }
        val priming = elst?.let { buf.mediaTime(it) } ?: 0L

        val mvex = boxes(buf, moov.body, moov.end).firstOrNull { it.type == "mvex" }
        val mehd = mvex?.let { boxes(buf, it.body, it.end).firstOrNull { box -> box.type == "mehd" } }
        val trex = mvex?.let { boxes(buf, it.body, it.end).firstOrNull { box -> box.type == "trex" } }
        val defaultDuration = trex?.let { buf.u32(it.body + 12) } ?: 0
        val defaultSize = trex?.let { buf.u32(it.body + 16) } ?: 0
        val mediaDuration = mehd?.let {
            if (buf.u8(it.body) == 1) buf.u64(it.body + 4) else buf.u32(it.body + 4).toLong()
        } ?: 0L

        val entries = boxes(buf, stsd.body + 8, stsd.end)
        require(entries.isNotEmpty()) { "empty stsd" }

        var encryptedEntry = 0
        var sampleRate = 0
        var channels = 0
        var asc: ByteArray? = null
        var kidBytes: ByteArray? = null
        var ivSize = 0
        var mime = ""

        entries.forEachIndexed { index, entry ->
            if (entry.type != "enca" && entry.type != "mp4a") return@forEachIndexed
            if (entry.type == "enca" && encryptedEntry == 0) encryptedEntry = index + 1
            if (sampleRate == 0) {
                channels = buf.u16(entry.body + 16)
                sampleRate = buf.u32(entry.body + 24) ushr 16
            }
            val children = boxes(buf, entry.body + 28, entry.end)
            if (asc == null) {
                children.firstOrNull { it.type == "esds" }?.let { asc = parseEsds(buf, it) }
            }
            children.firstOrNull { it.type == "sinf" }?.let { sinf ->
                val sinfChildren = boxes(buf, sinf.body, sinf.end)
                sinfChildren.firstOrNull { it.type == "schi" }?.let { schi ->
                    boxes(buf, schi.body, schi.end).firstOrNull { box -> box.type == "tenc" }?.let { tenc ->
                        ivSize = buf.u8(tenc.body + 7)
                        kidBytes = buf.copyOfRange(tenc.body + 8, tenc.body + 24)
                    }
                }
                val original = sinfChildren.firstOrNull { it.type == "frma" }
                    ?.let { String(buf, it.body, 4, Charsets.US_ASCII) }
                if (mime.isBlank()) mime = mimeOf(original ?: entry.type)
            }
            if (mime.isBlank()) mime = mimeOf(entry.type)
        }

        require(sampleRate > 0) { "no audio sample entry" }
        require(asc != null) { "no esds" }
        return Init(
            sampleRate = sampleRate,
            channels = channels,
            asc = asc!!,
            kid = kidBytes ?: ByteArray(0),
            ivSize = ivSize,
            timescale = timescale,
            defaultDuration = defaultDuration,
            defaultSize = defaultSize,
            encryptedEntry = encryptedEntry,
            priming = priming,
            mediaDuration = mediaDuration,
            mime = mime.ifBlank { "audio/mp4a-latm" },
        )
    }

    private fun ByteArray.isSound(trak: Box): Boolean {
        val mdia = boxes(this, trak.body, trak.end).firstOrNull { it.type == "mdia" } ?: return false
        val hdlr = boxes(this, mdia.body, mdia.end).firstOrNull { it.type == "hdlr" } ?: return false
        if (hdlr.end - hdlr.body < 12) return false
        return String(this, hdlr.body + 8, 4, Charsets.US_ASCII) == "soun"
    }

    private fun mimeOf(fourcc: String): String = when (fourcc) {
        "mp4a", "enca" -> "audio/mp4a-latm"
        "fLaC" -> "audio/flac"
        "Opus" -> "audio/opus"
        "alac" -> "audio/alac"
        "ac-3" -> "audio/ac3"
        "ec-3" -> "audio/eac3"
        else -> ""
    }

    fun parseFragment(buf: ByteArray, init: Init): Fragment? {
        val moof = boxes(buf, 0, buf.size).firstOrNull { it.type == "moof" } ?: return null
        val traf = boxes(buf, moof.body, moof.end).firstOrNull { it.type == "traf" } ?: return null
        val children = boxes(buf, traf.body, traf.end)
        val tfhd = children.firstOrNull { it.type == "tfhd" } ?: return null
        val trun = children.firstOrNull { it.type == "trun" } ?: return null
        val tfdt = children.firstOrNull { it.type == "tfdt" }
        val senc = children.firstOrNull { it.type == "senc" }

        val tfhdFlags = buf.u24(tfhd.start + 9)
        var p = tfhd.body + 8
        if (tfhdFlags and 0x01 != 0) p += 8
        var entry = 1
        if (tfhdFlags and 0x02 != 0) {
            entry = buf.u32(p)
            p += 4
        }
        var duration = init.defaultDuration
        if (tfhdFlags and 0x08 != 0) {
            duration = buf.u32(p)
            p += 4
        }
        var size = init.defaultSize
        if (tfhdFlags and 0x10 != 0) {
            size = buf.u32(p)
            p += 4
        }

        val baseTime = when {
            tfdt == null -> 0L
            buf.u8(tfdt.body) == 1 -> buf.u64(tfdt.body + 4)
            else -> buf.u32(tfdt.body + 4).toLong()
        }

        val trunFlags = buf.u24(trun.start + 9)
        var q = trun.body + 4
        val count = buf.u32(q)
        q += 4
        var dataOffset = -1
        if (trunFlags and 0x01 != 0) {
            dataOffset = buf.u32(q)
            q += 4
        }
        if (trunFlags and 0x04 != 0) q += 4

        val ivs = senc?.let { parseSenc(buf, it, count, init.ivSize) }
        var cursor = if (dataOffset >= 0) moof.start + dataOffset else -1
        if (cursor < 0) {
            cursor = boxes(buf, moof.end, buf.size).firstOrNull { it.type == "mdat" }?.body ?: return null
        }

        val samples = ArrayList<Sample>(count)
        for (i in 0 until count) {
            var sampleDuration = duration
            if (trunFlags and 0x100 != 0) {
                sampleDuration = buf.u32(q)
                q += 4
            }
            var sampleSize = size
            if (trunFlags and 0x200 != 0) {
                sampleSize = buf.u32(q)
                q += 4
            }
            if (trunFlags and 0x400 != 0) q += 4
            if (trunFlags and 0x800 != 0) q += 4
            samples += Sample(cursor, sampleSize, sampleDuration, ivs?.getOrNull(i)?.iv, ivs?.getOrNull(i)?.subsamples)
            cursor += sampleSize
        }
        return Fragment(entry, baseTime, samples)
    }

    private fun ByteArray.mediaTime(elst: Box): Long {
        val version = u8(elst.body)
        val count = u32(elst.body + 4)
        if (count == 0) return 0L
        return if (version == 1) u64(elst.body + 16) else u32(elst.body + 12).toLong()
    }

    private fun parseEsds(buf: ByteArray, box: Box): ByteArray? {
        var p = box.body + 4
        while (p + 2 < box.end) {
            val tag = buf.u8(p)
            var q = p + 1
            var length = 0
            var guard = 0
            while (q < box.end && guard < 4) {
                val b = buf.u8(q)
                length = (length shl 7) or (b and 0x7f)
                q++
                guard++
                if (b and 0x80 == 0) break
            }
            if (tag == 0x03) {
                var inner = q + 3
                if (q + 2 < box.end) {
                    val flags = buf.u8(q + 2)
                    if (flags and 0x80 != 0) inner += 2
                    if (flags and 0x40 != 0 && inner < box.end) inner += 1 + buf.u8(inner)
                    if (flags and 0x20 != 0) inner += 2
                }
                val found = parseEsdsRange(buf, inner, minOf(q + length, box.end))
                if (found != null) return found
            }
            p = q + length
        }
        return null
    }

    private fun parseEsdsRange(buf: ByteArray, from: Int, to: Int): ByteArray? {
        var p = from
        while (p + 2 < to) {
            val tag = buf.u8(p)
            var q = p + 1
            var length = 0
            var guard = 0
            while (q < to && guard < 4) {
                val b = buf.u8(q)
                length = (length shl 7) or (b and 0x7f)
                q++
                guard++
                if (b and 0x80 == 0) break
            }
            if (tag == 0x05) return buf.copyOfRange(q, minOf(q + length, to))
            if (tag == 0x04) {
                val found = parseEsdsRange(buf, q + 13, minOf(q + length, to))
                if (found != null) return found
            }
            p = q + length
        }
        return null
    }

    private fun parseSenc(buf: ByteArray, box: Box, count: Int, ivSize: Int): List<Protection?> {
        val flags = buf.u24(box.start + 9)
        val body = box.body + 8
        val entryBytes = box.end - body
        val per = if (count > 0) entryBytes / count else 0
        val size = if (flags and 0x02 == 0 && (per == 8 || per == 16)) per else ivSize
        var p = body
        val out = ArrayList<Protection?>(count)
        for (i in 0 until count) {
            if (p + size > box.end) {
                out += null
                continue
            }
            val iv = buf.copyOfRange(p, p + size).copyOf(16)
            p += size
            var subsamples: IntArray? = null
            if (flags and 0x02 != 0 && p + 2 <= box.end) {
                val pairs = buf.u16(p)
                p += 2
                val end = p + pairs * 6
                if (pairs > 0 && end <= box.end) {
                    subsamples = IntArray(pairs * 2)
                    for (pair in 0 until pairs) {
                        subsamples[pair * 2] = buf.u16(p)
                        subsamples[pair * 2 + 1] = buf.u32(p + 2)
                        p += 6
                    }
                } else {
                    p = minOf(end, box.end)
                }
            }
            out += Protection(iv, subsamples)
        }
        return out
    }
}

internal class Protection(val iv: ByteArray, val subsamples: IntArray?)
