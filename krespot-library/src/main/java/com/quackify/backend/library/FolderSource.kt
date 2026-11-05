package com.quackify.backend.library

import android.content.Context
import java.net.URLEncoder
import java.security.SecureRandom
import com.quackify.backend.core.log.Logs
import org.json.JSONArray

private val log = Logs.of(Logs.Module.FOLDER)

object FolderSource {

    fun folderHash(uri: String): String {
        if (uri.isBlank()) return ""
        val marker = ":folder:"
        val at = uri.indexOf(marker)
        if (at >= 0) return uri.substring(at + marker.length).trim()
        val args = uri.split(":")
        val group = args.indexOfFirst { it == "start-group" || it == "end-group" }
        if (group >= 0) return args.getOrNull(group + 1).orEmpty()
        return ""
    }

    fun folderUri(user: String, hash: String): String = "spotify:user:$user:folder:$hash"

    fun startGroup(hash: String, name: String): String =
        "spotify:start-group:$hash:${encode(name)}"

    fun endGroup(hash: String): String = "spotify:end-group:$hash"

    private fun newHash(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encode(name: String): String = URLEncoder.encode(name.trim(), "UTF-8")
        .replace("%21", "!")
        .replace("%2A", "*")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%7E", "~")

    suspend fun createFolder(context: Context, name: String): String? {
        val trimmed = name.trim().ifBlank { "New Folder" }
        val hash = newHash()
        val ok = Rootlist.addUris(
            context,
            listOf(startGroup(hash, trimmed), endGroup(hash)),
            atTop = true,
        )
        val user = Rootlist.username(context)
        log.i("createFolder '$trimmed' hash=$hash user=$user ok=$ok")
        return if (ok && user.isNotBlank()) folderUri(user, hash) else null
    }

    suspend fun renameFolder(context: Context, folderUri: String, name: String): Boolean {
        val trimmed = name.trim()
        val hash = folderHash(folderUri)
        if (trimmed.isBlank() || hash.isBlank()) {
            log.w("renameFolder: bad input '$folderUri' '$trimmed'")
            return false
        }
        val snap = Rootlist.snapshot(context) ?: return false
        val index = snap.groupIndex(hash, end = false)
        if (index < 0) {
            log.w("renameFolder: no start-group for $hash among ${snap.entries.size} entries")
            return false
        }
        val old = snap.entries.firstOrNull { it.index == index }?.uri.orEmpty()
        if (old.isBlank()) {
            log.w("renameFolder: empty entry at $index")
            return false
        }
        val ops = JSONArray()
            .put(Rootlist.remOp(old))
            .put(Rootlist.addAtOp(startGroup(hash, trimmed), index))
        val ok = Rootlist.writeOps(context, ops, snap.revision, "renameFolder")
        log.i("renameFolder $folderUri -> '$trimmed' at $index ok=$ok")
        return ok
    }

    suspend fun deleteFolder(context: Context, folderUri: String): Boolean {
        val hash = folderHash(folderUri)
        if (hash.isBlank()) {
            log.w("deleteFolder: no hash in '$folderUri'")
            return false
        }
        val snap = Rootlist.snapshot(context) ?: return false
        val start = snap.groupIndex(hash, end = false)
        val end = snap.groupIndex(hash, end = true)
        if (start < 0 || end < 0 || end <= start) {
            log.w("deleteFolder: markers start=$start end=$end for $hash")
            return false
        }
        val startUri = snap.entries.firstOrNull { it.index == start }?.uri.orEmpty()
        val endUri = snap.entries.firstOrNull { it.index == end }?.uri.orEmpty()
        if (startUri.isBlank() || endUri.isBlank()) {
            log.w("deleteFolder: empty marker uri at $start or $end")
            return false
        }
        val inside = end - start - 1
        val ops = JSONArray().put(Rootlist.remOp(startUri)).put(Rootlist.remOp(endUri))
        val ok = Rootlist.writeOps(context, ops, null, "deleteFolder")
        log.i("deleteFolder $folderUri ($hash) entries=$start..$end inside=$inside ok=$ok"
        )
        return ok
    }

    suspend fun moveIntoFolder(context: Context, uri: String, folderUri: String): Boolean {
        val hash = folderHash(folderUri)
        if (hash.isBlank() || uri.isBlank()) {
            log.w("moveIntoFolder: bad input '$uri' '$folderUri'")
            return false
        }
        val snap = Rootlist.snapshot(context) ?: return false
        val from = snap.indexOf(uri)
        val end = snap.groupIndex(hash, end = true)
        if (from < 0 || end < 0) {
            log.w("moveIntoFolder: from=$from end=$end among ${snap.entries.size} entries")
            return false
        }
        val to = end
        log.i("moveIntoFolder $uri from=$from to=$to endGroup=$end")
        return Rootlist.move(context, from, to, 1, snap.revision)
    }

    suspend fun removeFromFolder(context: Context, uri: String): Boolean {
        val snap = Rootlist.snapshot(context) ?: return false
        val from = snap.indexOf(uri)
        if (from <= 0) {
            log.w("removeFromFolder: $uri at $from, nothing to do")
            return from == 0
        }
        log.i("removeFromFolder $uri from=$from to=0")
        return Rootlist.move(context, from, 0, 1, snap.revision)
    }
}
