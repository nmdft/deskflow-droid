// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.Context
import java.net.Socket
import java.net.InetSocketAddress
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.io.OutputStream

/** Socket IO never runs on the accessibility or Deskflow network threads. */
class PhysicalKeyboard(context: Context) {
    private val endpoint = File(context.filesDir, "keyboard-endpoint")
    private val thread = HandlerThread("physical-keyboard").apply { start() }
    private val worker = Handler(thread.looper)
    private var socket: Socket? = null
    @Volatile
    private var output: OutputStream? = null
    val ready: Boolean get() = output != null
    private var lastFailure = 0L
    private val hid = HidKeyboard { report ->
        val out = requireNotNull(output)
        out.write(report.size); out.write(report); out.flush()
    }

    val configured: Boolean get() = endpoint.isFile

    private fun connect(): Boolean {
        if (output != null) return true
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFailure < 2000) return false
        val s = Socket()
        try {
            val address = endpoint.readText().trim().split(":", limit = 2)
            s.connect(InetSocketAddress("127.0.0.1", address[0].toInt()), 2000)
            s.soTimeout = 2000
            s.tcpNoDelay = true
            s.outputStream.write(address[1].toByteArray(Charsets.US_ASCII))
            s.outputStream.flush()
            check(s.inputStream.read() == 1) { "UHID initialization failed" }
            val leds = s.inputStream.read()
            check(leds >= 0) { "Missing keyboard state" }
            hid.initializeLocks(leds)
            socket = s; output = s.outputStream
            hid.reset()
            s.soTimeout = 0
            Thread({
                try { s.inputStream.read() } catch (_: Exception) { }
                finally {
                    worker.post {
                        if (socket === s) {
                            s.close(); socket = null; output = null
                            Logs.warn("Keyboard service stopped; basic text fallback active; restart for physical input")
                        }
                    }
                }
            }, "keyboard-liveness").apply { isDaemon = true; start() }
            Logs.add("Physical keyboard ready: select phone IME for Chinese; Mac input source ABC")
            return true
        } catch (e: Exception) {
            s.close(); output = null; socket = null; lastFailure = now
            Logs.warn("Physical keyboard unavailable: run Start Keyboard.command (${e.message})")
            return false
        }
    }

    private fun submit(onUnavailable: (() -> Unit)? = null, action: () -> Unit) {
        worker.post {
            if (!connect()) {
                onUnavailable?.invoke()
                return@post
            }
            try { action() } catch (e: Exception) {
                socket?.close(); socket = null; output = null
                Logs.warn("Physical keyboard disconnected; held keys released")
            }
        }
    }
    fun key(id: Int, mask: Int, button: Int, down: Boolean, onUnavailable: (() -> Unit)? = null) = submit(onUnavailable) {
        if (!hid.key(id, mask, button, down)) Logs.warn("Key has no HID equivalent; use Mac ABC and phone IME")
    }
    fun enter() { if (configured) submit { hid.reset() } }
    fun release() {
        worker.post { if (output != null) try { hid.reset() } catch (_: Exception) { socket?.close(); socket=null; output=null } }
    }
    fun close() {
        worker.post { socket?.close(); socket=null; output=null; thread.quitSafely() }
    }
}
