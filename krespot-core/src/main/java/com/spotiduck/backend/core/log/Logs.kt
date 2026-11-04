package com.spotiduck.backend.core.log

import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Logs {

    const val TAG = "SpotiDuck"

    /** How many log lines the in-app viewer keeps before the oldest fall off. */
    const val MAX_LINES = 3000

    enum class Module(val label: String) {
        AUTH("auth"),
        LOGIN("login"),
        LOGIN_FLOW("login-flow"),
        SILENT("silent"),
        WEBVIEW("webview"),
        CAPTURE("capture"),
        MAIN("main"),

        NET("net"),
        HASHES("hashes"),
        CLIENT_TOKEN("client-token"),
        PATHFINDER("pathfinder"),

        HOME("home"),
        SEARCH("search"),
        BROWSE("browse"),
        LIBRARY("library"),
        LIBRARY_PAGE("library-page"),
        COLLECTION("collection"),
        COLLECTION2("collection2"),
        PLAYLIST("playlist"),
        ALBUM("album"),
        ARTIST("artist"),
        FOLDER("folder"),
        ROOTLIST("rootlist"),
        TRACKS("tracks"),
        PODCASTS("podcasts"),
        WHATS_NEW("whats-new"),
        SETTINGS("settings"),
        LYRICS("lyrics"),
        HISTORY("history"),
        RECOMMENDATIONS("recommendations"),

        CONNECT("connect"),
        CLUSTER("cluster"),
        DEALER("dealer"),
        DEALER_HOST("dealer-host"),
        COMMAND("command"),
        REGISTER("register"),
        STATE("state"),
        MELODY("melody"),

        PLAYER("player"),
        ENGINE("engine"),
        SINK("sink"),
        FOCUS("focus"),
        SOURCE("source"),
        DRM("drm"),
        MP3("mp3"),

        BRIDGE("bridge"),
        HOST("host"),
        SERVICE("service"),

        SHELL("shell"),
        SHARE("share"),
    }

    enum class Level(val rank: Int) {
        VERBOSE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        OFF(5),
    }

    /** One captured log line, logcat style. */
    data class Entry(
        /** Monotonic counter, stable key for lazy lists. */
        val seq: Long,
        val wallClockMs: Long,
        val uptimeMs: Long,
        val module: Module,
        val level: Level,
        val message: String,
        val error: String?,
    ) {
        val levelLetter: String
            get() = when (level) {
                Level.VERBOSE -> "V"
                Level.DEBUG -> "D"
                Level.INFO -> "I"
                Level.WARN -> "W"
                Level.ERROR -> "E"
                Level.OFF -> "O"
            }
    }

    @Volatile
    var floor: Level = Level.VERBOSE

    private val modules = ConcurrentHashMap<Module, Level>()

    private val buffer = ArrayDeque<Entry>(MAX_LINES)
    private val gate = Any()
    private var nextSeq = 0L

    private val _revision = MutableStateFlow(0L)

    /** Bumped on every change so the viewer can recompose. */
    val revision: StateFlow<Long> = _revision

    init {
        modules[Module.MAIN] = Level.INFO
    }

    fun level(module: Module, level: Level) {
        modules[module] = level
    }

    fun enabled(module: Module, level: Level): Boolean = level.rank >= (modules[module] ?: floor).rank

    fun of(module: Module): Logger = Logger(module)

    /** Lines currently kept, oldest first. */
    fun snapshot(): List<Entry> = synchronized(gate) { buffer.toList() }

    /** Number of lines currently kept. */
    fun size(): Int = synchronized(gate) { buffer.size }

    /** Drops every captured line. */
    fun clear() {
        synchronized(gate) { buffer.clear() }
        _revision.value = _revision.value + 1L
    }

    /** Renders one entry the way logcat would print it. */
    fun format(entry: Entry): String {
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(entry.wallClockMs))
        val line = "$stamp ${entry.levelLetter}/SpotiDuck: [${entry.module.label}] ${entry.message}"
        return if (entry.error.isNullOrBlank()) line else "$line\n${entry.error}"
    }

    /** Renders every kept entry, one per line block. */
    fun dump(): String = snapshot().joinToString("\n") { format(it) }

    internal fun write(module: Module, level: Level, message: String, error: Throwable?) {
        record(module, level, message, error)
        val line = "[${module.label}] $message"
        when (level) {
            Level.VERBOSE -> Log.v(TAG, line, error)
            Level.DEBUG -> Log.d(TAG, line, error)
            Level.INFO -> Log.i(TAG, line, error)
            Level.WARN -> Log.w(TAG, line, error)
            Level.ERROR -> Log.e(TAG, line, error)
            Level.OFF -> Unit
        }
    }

    private fun record(module: Module, level: Level, message: String, error: Throwable?) {
        val compact = if (error == null) null else describe(error)
        synchronized(gate) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(
                Entry(
                    seq = nextSeq++,
                    wallClockMs = System.currentTimeMillis(),
                    uptimeMs = SystemClock.uptimeMillis(),
                    module = module,
                    level = level,
                    message = message,
                    error = compact,
                )
            )
        }
        _revision.value = _revision.value + 1L
    }

    private fun describe(error: Throwable): String {
        val head = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        val frames = error.stackTrace.take(6).joinToString("\n") { "    at $it" }
        val cause = error.cause
        val tail = if (cause != null) "\nCaused by: ${describe(cause)}" else ""
        return listOf(head, frames).joinToString("\n") + tail
    }
}

class Logger internal constructor(private val module: Logs.Module) {

    fun v(message: String) = send(Logs.Level.VERBOSE, message)

    fun d(message: String) = send(Logs.Level.DEBUG, message)

    fun i(message: String) = send(Logs.Level.INFO, message)

    fun w(message: String, error: Throwable? = null) = send(Logs.Level.WARN, message, error)

    fun e(message: String, error: Throwable? = null) = send(Logs.Level.ERROR, message, error)

    private fun send(level: Logs.Level, message: String, error: Throwable? = null) {
        if (!Logs.enabled(module, level)) return
        Logs.write(module, level, message, error)
    }
}
