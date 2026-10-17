package com.itesaurabh.deskflowclient

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app log tail, mirroring the pane the Deskflow desktop app shows under its controls. */
object Logs {
    private const val TAG = "Deskflow"
    private const val MAX_LINES = 400

    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val main = Handler(Looper.getMainLooper())

    enum class Level { Info, Warn }

    data class Entry(val time: String, val text: String, val level: Level)

    val lines = mutableStateListOf<Entry>()

    fun add(text: String) = append(text, Level.Info)

    /** Something did not work. Reserved for that, so the pane can pick it out. */
    fun warn(text: String) = append(text, Level.Warn)

    fun clear() {
        main.post { lines.clear() }
    }

    private fun append(text: String, level: Level) {
        // Also to logcat: the in-app pane dies with the process, which is exactly when the
        // interesting failures happen.
        if (level == Level.Warn) Log.w(TAG, text) else Log.i(TAG, text)

        val entry = Entry(clock.format(Date()), text, level)
        main.post {
            lines.add(entry)
            while (lines.size > MAX_LINES) {
                lines.removeAt(0)
            }
        }
    }
}
