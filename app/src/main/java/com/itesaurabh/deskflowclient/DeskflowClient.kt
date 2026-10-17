// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Owns the socket and drives the injector. Runs on its own thread; the injector is responsible
 * for hopping to whatever thread its backend needs.
 *
 * [secure] wraps the connected TCP socket in TLS. Deskflow handshakes immediately on connect,
 * before the hello packet, so this happens before anything protocol-shaped is read or written.
 */
class DeskflowClient(
    private val host: String,
    private val port: Int,
    private val clientName: String,
    private val injector: Injector,
    private val secure: ((Socket) -> Socket)?
) : Thread("deskflow-client") {

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var stopping = false

    private val pendingClipboard = java.util.concurrent.LinkedBlockingQueue<String>(1)
    @Volatile private var sequence = 0
    private val clipboardAssembly = ClipboardAssembly()
    fun sendClipboard(text: String) {
        if (text.toByteArray(Charsets.UTF_8).size > ClipboardWire.LIMIT - 12) {
            Logs.warn("Clipboard too large; text limit is 1 MiB")
            return
        }
        pendingClipboard.clear()
        pendingClipboard.offer(text)
    }

    private var reportedWidth = -1
    private var reportedHeight = -1
    private var serverClosed = false
    private var closeReason = "disconnected"

    /**
     * Stop for good. Closing the socket is what unblocks a thread parked in a read.
     *
     * The close happens on its own thread because closing an SSLSocket writes a close_notify alert
     * to the peer (real network I/O), and every caller of this is a button handler on the main
     * thread, where that trips StrictMode and takes the process down.
     */
    fun disconnect() {
        stopping = true
        val open = socket
        socket = null
        interrupt()
        if (open != null) {
            Thread({ closeQuietly(open) }, "deskflow-close").start()
        }
    }

    /** Only ever called from the client's own thread, where closing inline is fine. */
    private fun closeSocket() = closeQuietly(socket)

    private fun closeQuietly(target: Socket?) {
        try {
            target?.close()
        } catch (e: IOException) {
            // closing a dead socket is not interesting
        }
    }

    /**
     * Bringing up a TLS connection takes more than one attempt by design: the phone drops the
     * connection to ask about the server's fingerprint, and the desktop drops it again while it
     * asks about this device. Retrying keeps that from turning into repeated Connect presses.
     */
    override fun run() {
        while (!stopping) {
            if (!attempt() || stopping) {
                return
            }
            report("reconnecting in ${RETRY_DELAY_MS / 1000}s", ClientState.Phase.Connecting)
            try {
                sleep(RETRY_DELAY_MS)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    /** @return whether the failure is worth another attempt. */
    private fun attempt(): Boolean {
        var reason = "disconnected"
        var retry = true
        var clipboardSender: Thread? = null
        serverClosed = false
        sequence = 0
        pendingClipboard.clear()
        clipboardAssembly.reset()
        try {
            report("connecting to $host:$port", ClientState.Phase.Connecting)
            var s: Socket = Socket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.tcpNoDelay = true
            socket = s

            if (secure != null) {
                // The read timeout has to be armed before the handshake, not after. A server with
                // TLS switched off never answers the ClientHello, and an unarmed socket then blocks
                // in startHandshake forever: no error, no prompt, nothing.
                s.soTimeout = HANDSHAKE_TIMEOUT_MS
                Logs.add("starting TLS handshake")
                s = try {
                    secure.invoke(s)
                } catch (e: SocketTimeoutException) {
                    throw IOException("TLS handshake timed out — is TLS enabled on the server?", e)
                }
                socket = s
                Logs.add("TLS established")
            }
            // The desktop may be sitting on its own "trust this device?" prompt before it sends
            // hello, and a human takes longer than a keep-alive interval to answer it.
            s.soTimeout = HELLO_TIMEOUT_MS

            val input = DataInputStream(BufferedInputStream(s.getInputStream()))
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))

            handshake(input, out)
            clipboardSender = Thread({
                try {
                    while (!stopping && !s.isClosed) {
                        val text = pendingClipboard.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                        synchronized(out) {
                            if (!stopping && !s.isClosed)
                                for (packet in ClipboardWire.packets(text, sequence)) Protocol.writePacket(out, packet)
                        }
                    }
                } catch (_: InterruptedException) {
                    // Connection teardown interrupts the sender.
                } catch (_: IOException) {
                    closeQuietly(s) // Unblock the reader and use the normal reconnect path.
                }
            }, "deskflow-clipboard").also { it.start() }

            // The server sends CALV every 3s once connected, so silence this long means it is gone.
            s.soTimeout = READ_TIMEOUT_MS
            while (!stopping && !serverClosed) {
                dispatch(Reader(Protocol.readPacket(input)), out)
            }
            reason = closeReason
        } catch (e: SocketTimeoutException) {
            reason = "server went silent"
        } catch (e: Exception) {
            if (!stopping) {
                Log.w(TAG, "connection lost", e)
                // SSLHandshakeException messages are usually terse or null, and the useful detail
                // is always further down the chain, so log the whole thing.
                Logs.warn(describe(e))
                val untrusted = untrustedCause(e)
                when {
                    // The trust prompt drives the next attempt, so retrying here would just race it.
                    untrusted != null -> {
                        reason = "untrusted server certificate"
                        retry = false
                        ClientState.untrusted(untrusted.fingerprint)
                    }

                    // Misconfiguration. Retrying only fills the log with the same line.
                    e is FatalProtocolError -> {
                        reason = e.message ?: "protocol error"
                        retry = false
                    }

                    else -> reason = e.message ?: e.javaClass.simpleName
                }
            }
        } finally {
            clipboardSender?.interrupt()
            injector.leave()
            closeSocket()
            try { clipboardSender?.join(1000) } catch (_: InterruptedException) { /* Explicit disconnect. */ }
            report(reason, ClientState.Phase.Idle)
        }
        return retry && !stopping
    }

    private fun report(line: String, phase: ClientState.Phase? = null) {
        Logs.add(line)
        ClientState.report(line, phase)
    }

    private fun handshake(input: DataInputStream, out: DataOutputStream) {
        val hello = Reader(Protocol.readPacket(input))
        // "Barrier" or "Synergy", both 7 bytes. Echo whatever the server used.
        val protocolName = hello.bytes(7)
        val serverMajor = hello.i2()
        val serverMinor = hello.i2()
        val name = String(protocolName, Charsets.US_ASCII)

        if (serverMajor != Protocol.MAJOR) {
            throw IOException("server speaks $name $serverMajor.$serverMinor, we speak ${Protocol.MAJOR}.x")
        }
        val minor = minOf(Protocol.MINOR, serverMinor)
        if (minor < Protocol.MIN_SUPPORTED_MINOR) {
            throw IOException("server protocol $name $serverMajor.$serverMinor is too old")
        }

        Protocol.writePacket(out, Protocol.helloBack(protocolName, minor, clientName))
        report("$name $serverMajor.$minor, waiting for handshake")
    }

    private fun sendInfo(out: DataOutputStream) {
        val (w, h) = injector.screenSize()
        reportedWidth = w
        reportedHeight = h
        Logs.add("DINF ${w}x$h")
        Protocol.writePacket(out, Protocol.info(0, 0, w, h, w / 2, h / 2))
    }

    private fun dispatch(r: Reader, out: DataOutputStream) {
        val code = r.code()
        // Keep writes on the socket thread; CALV also wakes this after an idle rotation.
        if (code != "QINF" && (reportedWidth != injector.screenWidth || reportedHeight != injector.screenHeight)) {
            sendInfo(out)
        }
        when (code) {
            "CALV" -> Protocol.writePacket(out, Protocol.KEEP_ALIVE)
            "QINF" -> sendInfo(out)
            "CIAK" -> report("connected as \"$clientName\"", ClientState.Phase.Connected)

            "CINN" -> {
                val x = r.i2()
                val y = r.i2()
                sequence = r.i4()
                r.u2() // modifier mask on entry
                Logs.add("CINN cursor entered at $x,$y")
                injector.enter(x, y)
            }

            "COUT" -> {
                Logs.add("COUT cursor left")
                injector.leave()
            }

            "DMMV" -> injector.moveTo(r.i2(), r.i2())
            "DMRM" -> injector.moveBy(r.i2(), r.i2())
            "DMDN" -> injector.mouseDown(r.i1())
            "DMUP" -> injector.mouseUp(r.i1())
            "DMWM" -> injector.wheel(r.i2(), r.i2())

            "DKDN", "DKDL" -> injector.keyDown(r.u2(), r.u2(), r.u2())
            "DKUP" -> injector.keyUp(r.u2(), r.u2(), r.u2())

            "DKRP" -> {
                val id = r.u2()
                val mask = r.u2()
                val count = r.u2()
                val button = r.u2()
                injector.keyRepeat(id, mask, button, count.coerceAtMost(MAX_KEY_REPEAT))
            }

            "DCLP" -> clipboardAssembly.receive(r)?.let { injector.clipboard(it) }
            "CCLP" -> { r.i1(); r.i4() } // Ownership notice; actual text arrives in DCLP.

            // Drag-drop, files and layout sync are not implemented.
            "CNOP", "DSOP", "CROP", "CSEC", "DDRG", "DFTR", "LSYN", "SECN" -> Unit

            "CBYE" -> {
                closeReason = "server closed the connection"
                serverClosed = true
            }

            // EBSY is usually our own previous connection not yet reaped, so that one is worth
            // waiting out. The rest need a human to change something.
            "EBSY" -> throw IOException("a client with this name is already connected")
            "EICV" -> throw FatalProtocolError("incompatible protocol versions")
            "EUNK" -> throw FatalProtocolError("server does not know a screen named \"$clientName\"")
            "EBAD" -> throw FatalProtocolError("server rejected our message as malformed")

            else -> Logs.add("ignoring unknown message $code")
        }
    }

    private class FatalProtocolError(message: String) : IOException(message)

    private fun describe(e: Throwable): String {
        val chain = mutableListOf<String>()
        var cause: Throwable? = e
        while (cause != null && chain.size < 6) {
            chain += "${cause.javaClass.simpleName}: ${cause.message ?: "(no message)"}"
            cause = cause.cause
        }
        return chain.joinToString(" <- ")
    }

    /** JSSE wraps trust-manager failures in SSLHandshakeException, so dig for ours. */
    private fun untrustedCause(e: Throwable): UntrustedServer? {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is UntrustedServer) return cause
            cause = cause.cause
        }
        return null
    }

    private companion object {
        const val TAG = "DeskflowClient"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val HANDSHAKE_TIMEOUT_MS = 10_000
        const val HELLO_TIMEOUT_MS = 90_000
        const val READ_TIMEOUT_MS = 15_000
        const val RETRY_DELAY_MS = 3_000L
        const val MAX_KEY_REPEAT = 64
    }
}
