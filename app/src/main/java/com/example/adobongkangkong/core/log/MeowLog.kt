package com.example.adobongkangkong.core.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes to Logcat AND app-private file (filesDir/meow.log).
 * Intended for debug/diagnostics.
 */
object MeowLog {

    private const val DEFAULT_TAG = "Meow"
    private const val FILE_NAME = "meow.log"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var appContext: Context? = null

    /**
     * Call once from Application.onCreate().
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun d(msg: String, tag: String = DEFAULT_TAG) {
        Log.d(tag, msg)
        appendToFile("D", tag, msg, null)
    }

    fun e(msg: String, tr: Throwable? = null, tag: String = DEFAULT_TAG) {
        Log.e(tag, msg, tr)
        appendToFile("E", tag, msg, tr)
    }

    private fun appendToFile(level: String, tag: String, msg: String, tr: Throwable?) {
        val ctx = appContext ?: return // fail silent if not initialized

        // Keep log compact but useful
        val now = tsFmt.format(Date())
        val stack = tr?.stackTraceToString()?.let { "\n$it" }.orEmpty()
        val line = "$now $level/$tag: $msg$stack\n"

        scope.launch {
            runCatching {
                val file = File(ctx.filesDir, FILE_NAME)
                file.appendText(line)

                // Simple size cap: keep last ~1MB
                capFileSize(file, maxBytes = 1_000_000)
            }
        }
    }

    private fun capFileSize(file: File, maxBytes: Int) {
        if (!file.exists()) return
        val len = file.length()
        if (len <= maxBytes) return

        // Keep last maxBytes worth of bytes
        val bytes = file.readBytes()
        val start = (bytes.size - maxBytes).coerceAtLeast(0)
        val trimmed = bytes.copyOfRange(start, bytes.size)
        file.writeBytes(trimmed)
    }

    fun getLogFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun clear(context: Context) {
        runCatching { getLogFile(context).writeText("") }
    }
}
