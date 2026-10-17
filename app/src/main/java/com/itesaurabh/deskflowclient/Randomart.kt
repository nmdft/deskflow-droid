package com.itesaurabh.deskflowclient

/**
 * The "drunken bishop" art OpenSSH popularised, reproduced to match Deskflow's own
 * `generateFingerprintArt`, same 17x9 field, same character pool. It has to match: the whole
 * point is holding this next to the desktop and seeing the same picture.
 *
 * Deskflow frames its output with box-drawing characters. We return the rows bare and let the UI
 * draw the frame, because those characters do not share the monospace advance width and the box
 * came out ragged.
 */
object Randomart {

    private const val COLUMNS = 17
    private const val ROWS = 9
    private const val POOL = " .o+=*BOX@%&#/^SE"

    fun of(digest: ByteArray): String {
        val last = POOL.length - 1 // 16
        val field = Array(COLUMNS) { IntArray(ROWS) }

        var x = COLUMNS / 2
        var y = ROWS / 2

        for (raw in digest) {
            // Each byte carries four 2-bit moves, least significant pair first.
            var byte = raw.toInt() and 0xFF
            repeat(4) {
                x += if (byte and 0x1 != 0) 1 else -1
                y += if (byte and 0x2 != 0) 1 else -1
                x = x.coerceIn(0, COLUMNS - 1)
                y = y.coerceIn(0, ROWS - 1)
                if (field[x][y] < last - 2) {
                    field[x][y]++
                }
                byte = byte shr 2
            }
        }

        // Start and end markers, 'S' and 'E', always win over the visit count.
        field[COLUMNS / 2][ROWS / 2] = last - 1
        field[x][y] = last

        return (0 until ROWS).joinToString("\n") { row ->
            (0 until COLUMNS)
                .map { col -> POOL[minOf(field[col][row], last)] }
                .joinToString("")
        }
    }
}
