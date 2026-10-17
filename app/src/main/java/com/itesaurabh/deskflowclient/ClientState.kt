package com.itesaurabh.deskflowclient

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the screen needs to know about the connection. Written from the socket thread and read
 * during composition, so every write hops to the main thread.
 *
 * This replaces the callbacks the activity used to register on the service: the state outlives the
 * activity either way, and reading it directly means nothing to subscribe to or leak.
 */
object ClientState {

    enum class Phase { Idle, Connecting, Connected }

    private val main = Handler(Looper.getMainLooper())

    var status by mutableStateOf("Not connected")
        private set

    var phase by mutableStateOf(Phase.Idle)
        private set

    /** The server's fingerprint, while it is waiting on a trust decision. */
    var untrustedFingerprint by mutableStateOf<String?>(null)
        private set

    fun report(text: String, newPhase: Phase? = null) {
        main.post {
            status = text
            if (newPhase != null) phase = newPhase
        }
    }

    fun untrusted(fingerprint: String?) {
        main.post { untrustedFingerprint = fingerprint }
    }
}
