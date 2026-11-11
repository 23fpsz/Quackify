package com.quackify.backend.connect.cluster

import android.content.Context
import com.quackify.backend.core.store.SecureStore
import java.security.SecureRandom
import com.quackify.backend.core.log.Logs

private val log = Logs.of(Logs.Module.CLUSTER)

object Device {

    const val CLIENT_ID = "d8a5ed958d274c2e8ee717e6a4b0971d"

    private const val KEY_ID = "connect_device_id"


    private val random = SecureRandom()

    @Volatile
    private var cached: String? = null

    fun id(context: Context): String {
        cached?.let { return it }
        val prefs = SecureStore.prefs(context)
        val stored = prefs.getString(KEY_ID, "").orEmpty()
        if (stored.isNotBlank()) {
            cached = stored
            log.i("device id reused ${stored.take(8)}")
            return stored
        }
        val minted = newId()
        prefs.edit().putString(KEY_ID, minted).apply()
        cached = minted
        log.i("device id minted ${minted.take(8)}")
        return minted
    }

    fun forget(context: Context) {
        cached = null
        SecureStore.edit(context).remove(KEY_ID).apply()
        log.i("device id dropped")
    }

    fun newId(): String {
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
