// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import java.io.ByteArrayOutputStream
import java.io.IOException

/** Deskflow 1.8 text format and start/data/end chunks; limit applies to the whole transfer. */
object ClipboardWire {
    const val LIMIT = 1024 * 1024
    fun encode(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= LIMIT - 12) { "Clipboard text exceeds 1 MiB" }
        return Packet().i4(1).i4(0).blob(bytes).bytes()
    }
    fun decode(bytes: ByteArray): String? {
        val r = Reader(bytes)
        val count = r.i4()
        if (count !in 0..16) throw IOException("invalid clipboard format count")
        var text: String? = null
        repeat(count) {
            val format = r.i4()
            val data = r.blob()
            if (format == 0) text = String(data, Charsets.UTF_8)
        }
        if (r.remaining != 0) throw IOException("trailing clipboard data")
        return text
    }
    fun packets(text: String, sequence: Int): List<ByteArray> {
        val bytes = encode(text)
        fun chunk(mark: Int, data: ByteArray) = Packet().code("DCLP").i1(0).i4(sequence)
            .i1(mark).blob(data).bytes()
        return buildList {
            add(Packet().code("CCLP").i1(0).i4(sequence).bytes())
            add(chunk(1, bytes.size.toString().toByteArray(Charsets.US_ASCII)))
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + 32768, bytes.size)
                add(chunk(2, bytes.copyOfRange(offset, end)))
                offset = end
            }
            add(chunk(3, byteArrayOf()))
        }
    }
}

class ClipboardAssembly {
    private var id = -1
    private var sequence = 0
    private var expected = -1L
    private val data = ByteArrayOutputStream()
    fun reset() { id = -1; expected = -1L; data.reset() }
    fun receive(r: Reader): String? {
        val incomingId = r.i1()
        val incomingSequence = r.i4()
        val mark = r.i1()
        val part = r.blob()
        if (r.remaining != 0 || incomingId !in 0..1) throw IOException("invalid clipboard chunk")
        when (mark) {
            1 -> {
                reset()
                expected = String(part, Charsets.US_ASCII).toLongOrNull()
                    ?.takeIf { it >= 4 } ?: throw IOException("invalid clipboard size")
                id = incomingId; sequence = incomingSequence
            }
            2, 3 -> {
                if (expected < 0 || incomingId != id || incomingSequence != sequence)
                    throw IOException("clipboard chunk out of sequence")
                // Large images/text must not break an otherwise healthy keyboard connection.
                if (expected > ClipboardWire.LIMIT) {
                    if (mark == 3) reset()
                    return null
                }
                if (mark == 2) {
                    if (part.size > expected - data.size()) throw IOException("clipboard exceeds declared size")
                    data.write(part)
                } else {
                    if (part.isNotEmpty() || data.size().toLong() != expected) throw IOException("incomplete clipboard")
                    val bytes = data.toByteArray()
                    val standardClipboard = id == 0
                    reset()
                    return if (standardClipboard) ClipboardWire.decode(bytes) else null
                }
            }
            else -> throw IOException("invalid clipboard chunk type")
        }
        return null
    }
}
