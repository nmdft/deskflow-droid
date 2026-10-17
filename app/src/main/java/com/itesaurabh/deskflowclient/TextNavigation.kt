// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

/** UTF-16 editor selection with code-point-safe left/right movement. */
object TextNavigation {
    fun move(value: String, selectionStart: Int, selectionEnd: Int, id: Int, mask: Int): Pair<Int, Int> {
        val anchor = selectionStart.coerceIn(0, value.length)
        val cursor = selectionEnd.coerceIn(0, value.length)
        val shift = mask and KeyModifier.SHIFT != 0
        val ctrl = mask and (KeyModifier.CONTROL or KeyModifier.META or KeyModifier.SUPER) != 0
        fun previous(pos: Int) = if (pos == 0) 0 else value.offsetByCodePoints(pos, -1)
        fun next(pos: Int) = if (pos == value.length) pos else value.offsetByCodePoints(pos, 1)
        val target = when (id) {
            KeyId.HOME -> 0
            KeyId.END -> value.length
            KeyId.LEFT -> if (!shift && anchor != cursor) minOf(anchor, cursor) else if (ctrl) {
                var pos = cursor
                while (pos > 0 && value[previous(pos)].isWhitespace()) pos = previous(pos)
                while (pos > 0 && !value[previous(pos)].isWhitespace()) pos = previous(pos)
                pos
            } else previous(cursor)
            KeyId.RIGHT -> if (!shift && anchor != cursor) maxOf(anchor, cursor) else if (ctrl) {
                var pos = cursor
                while (pos < value.length && !value[pos].isWhitespace()) pos = next(pos)
                while (pos < value.length && value[pos].isWhitespace()) pos = next(pos)
                pos
            } else next(cursor)
            KeyId.UP, KeyId.DOWN -> {
                val lineStart = value.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (cursor == 0) 0 else it + 1 }
                val column = cursor - lineStart
                if (id == KeyId.UP) {
                    if (lineStart == 0) 0 else {
                        val previousEnd = lineStart - 1
                        val previousStart = value.lastIndexOf('\n', previousEnd - 1) + 1
                        minOf(previousStart + column, previousEnd)
                    }
                } else {
                    val lineEnd = value.indexOf('\n', cursor)
                    if (lineEnd < 0) value.length else {
                        val nextStart = lineEnd + 1
                        val nextEnd = value.indexOf('\n', nextStart).let { if (it < 0) value.length else it }
                        minOf(nextStart + column, nextEnd)
                    }
                }
            }
            else -> cursor
        }
        return (if (shift) anchor else target) to target
    }
}
