package com.orionisli.gh0stra1n

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    SU,
}

data class LogEntry(
    val time: String,
    val level: LogLevel,
    val tag: String,
    val msg: String,
) {
    val formatted: String get() = "[$time] [$tag] $msg"
}

object AppLogger {
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
    private val logHistory = Collections.synchronizedList(mutableListOf<LogEntry>())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val mainHandler = Handler(Looper.getMainLooper())

    fun log(level: LogLevel, tag: String, msg: String) {
        val time = timeFormat.format(Date())
        val entry = LogEntry(time, level, tag, msg)
        logHistory.add(entry)
        if (logHistory.size > 800) {
            logHistory.removeAt(0)
        }
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener(entry)
                } catch (_: Exception) {}
            }
        }
        when (level) {
            LogLevel.ERROR -> android.util.Log.e("Gh0stra1n", entry.formatted)
            LogLevel.WARN -> android.util.Log.w("Gh0stra1n", entry.formatted)
            LogLevel.DEBUG -> android.util.Log.d("Gh0stra1n", entry.formatted)
            else -> android.util.Log.i("Gh0stra1n", entry.formatted)
        }
    }

    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(LogLevel.ERROR, tag, msg)

    fun su(cmd: String, exit: Int, out: String) {
        val cleanOut = out.trim()
        val snippet = if (cleanOut.length > 120) {
            cleanOut.take(120).replace("\n", " ") + "…"
        } else {
            cleanOut.replace("\n", " ")
        }
        val status = if (exit == 0) "OK" else "RC=$exit"
        val outDesc = if (snippet.isNotBlank()) " -> \"$snippet\"" else ""
        val level = if (exit == 0) LogLevel.SU else LogLevel.ERROR
        log(level, "SU", "$ $cmd [$status]$outDesc")
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun getAllLogs(): List<LogEntry> = synchronized(logHistory) { logHistory.toList() }

    fun clear() {
        synchronized(logHistory) {
            logHistory.clear()
        }
        log(LogLevel.INFO, "INFO", "终端日志已清空")
    }
}
