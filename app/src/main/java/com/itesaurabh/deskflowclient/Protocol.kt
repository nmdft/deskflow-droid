// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Synergy 1.6 wire format, unchanged in Deskflow.
 *
 * Every packet is a 4-byte big-endian length followed by a payload whose first 4 bytes are the
 * ASCII message code. The hello packet is framed the same way: Client.cpp wraps the socket in
 * PacketStreamFilter before the handshake, and that filter length-prefixes everything.
 *
 * Format specifiers in the upstream constants map to the readers/writers below:
 * %1i/%2i/%4i are fixed-width big-endian ints, %s is a 4-byte-length-prefixed byte string, and
 * %7s is a bare 7-byte field used only by hello.
 */
object Protocol {
    const val DEFAULT_PORT = 24800
    const val MAJOR = 1
    const val MINOR = 8

    /** Below this the DKDN/DKUP/DMWM payloads have a different shape; not worth supporting. */
    const val MIN_SUPPORTED_MINOR = 6

    private const val MAX_PACKET = 4 * 1024 * 1024

    fun readPacket(input: DataInputStream): ByteArray {
        val len = input.readInt()
        if (len < 4 || len > MAX_PACKET) throw IOException("bad packet length $len")
        return ByteArray(len).also { input.readFully(it) }
    }

    fun writePacket(out: DataOutputStream, body: ByteArray) {
        synchronized(out) {
            out.writeInt(body.size)
            out.write(body)
            out.flush()
        }
    }

    fun helloBack(protocolName: ByteArray, minor: Int, clientName: String): ByteArray =
        Packet().raw(protocolName).i2(MAJOR).i2(minor).str(clientName).bytes()

    fun info(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int): ByteArray =
        Packet().code("DINF").i2(x).i2(y).i2(w).i2(h).i2(0).i2(mx).i2(my).bytes()

    val KEEP_ALIVE: ByteArray = Packet().code("CALV").bytes()
}

/** Cursor over a received payload. Read fields in the order the format string lists them. */
class Reader(private val b: ByteArray) {
    private var p = 0

    fun code(): String = String(bytes(4), Charsets.US_ASCII)

    val remaining: Int get() = b.size - p

    fun blob(): ByteArray = bytes(i4())

    fun bytes(n: Int): ByteArray {
        if (n < 0 || n > b.size - p) throw IOException("payload truncated")
        return b.copyOfRange(p, p + n).also { p += n }
    }

    private fun u1(): Int {
        if (p >= b.size) throw IOException("payload truncated")
        return b[p++].toInt() and 0xFF
    }

    fun i1(): Int = u1().toByte().toInt()
    fun u2(): Int = (u1() shl 8) or u1()
    fun i2(): Int = u2().toShort().toInt()
    fun i4(): Int = (u2() shl 16) or u2()
}

class Packet {
    private val o = ByteArrayOutputStream()

    fun code(c: String) = raw(c.toByteArray(Charsets.US_ASCII))
    fun raw(b: ByteArray) = apply { o.write(b) }
    fun i1(v: Int) = apply { o.write(v and 0xFF) }
    fun blob(b: ByteArray) = apply { i4(b.size); raw(b) }
    fun i2(v: Int) = apply { o.write((v ushr 8) and 0xFF); o.write(v and 0xFF) }
    fun i4(v: Int) = apply { i2((v ushr 16) and 0xFFFF); i2(v and 0xFFFF) }
    fun str(s: String) = apply { val b = s.toByteArray(Charsets.UTF_8); i4(b.size); raw(b) }
    fun bytes(): ByteArray = o.toByteArray()
}

/**
 * KeyIDs are UTF-32 codepoints, except U+E000..U+EFFF which are control keys.
 * Per KeyTypes.h every control identifier outside 0xE000..0xE0FF is the X11 keysym minus 0x1000.
 */
object KeyId {
    const val BACKSPACE = 0xEF08
    const val TAB = 0xEF09
    const val RETURN = 0xEF0D
    const val ESCAPE = 0xEF1B
    const val HOME = 0xEF50
    const val LEFT = 0xEF51
    const val UP = 0xEF52
    const val RIGHT = 0xEF53
    const val DOWN = 0xEF54
    const val END = 0xEF57
    const val DELETE = 0xEFFF

    private val CONTROL = 0xE000..0xEFFF

    /** Synergy's numeric keypad uses private KeyIDs, not Unicode digits. */
    fun normalize(id: Int): Int = when (id) {
        in 0xEFB0..0xEFB9 -> '0'.code + id - 0xEFB0
        0xEF80 -> ' '.code
        0xEF89 -> TAB
        0xEF8D -> RETURN
        0xEF95 -> HOME
        0xEF96 -> LEFT
        0xEF97 -> UP
        0xEF98 -> RIGHT
        0xEF99 -> DOWN
        0xEF9C -> END
        0xEF9F -> DELETE
        0xEFAA -> '*'.code
        0xEFAB -> '+'.code
        0xEFAC -> ','.code
        0xEFAD -> '-'.code
        0xEFAE -> '.'.code
        0xEFAF -> '/'.code
        0xEFBD -> '='.code
        else -> id
    }

    fun isPrintable(id: Int): Boolean = id >= 0x20 && id !in CONTROL && id != 0x7F
}

object KeyModifier {
    const val SHIFT = 0x0001
    const val CONTROL = 0x0002
    const val ALT = 0x0004
    const val META = 0x0008
    const val SUPER = 0x0010
    const val ALT_GR = 0x0020

    /** Anything here means a shortcut, which the accessibility backend cannot express. */
    const val SHORTCUT = CONTROL or ALT or META or SUPER
}
