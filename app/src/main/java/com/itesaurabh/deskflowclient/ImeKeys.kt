// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

/** Android key-code numbers without Android runtime dependencies, so mappings are unit tested. */
object ImeKeys {
    fun code(raw: Int): Int? = when (val id = KeyId.normalize(raw)) {
        0xEFE1 -> 59 // Shift left
        0xEFE2 -> 60
        0xEFE3, 0xEFE7, 0xEFEB -> 113 // Ctrl / Command left
        0xEFE4, 0xEFE8, 0xEFEC -> 114
        0xEFE9 -> 57 // Alt left
        0xEFEA -> 58
        KeyId.BACKSPACE -> 67
        KeyId.DELETE -> 112
        KeyId.RETURN -> 66
        KeyId.TAB -> 61
        KeyId.ESCAPE -> 111
        KeyId.LEFT -> 21
        KeyId.RIGHT -> 22
        KeyId.UP -> 19
        KeyId.DOWN -> 20
        KeyId.HOME -> 122
        KeyId.END -> 123
        0xEF55 -> 92 // Page up
        0xEF56 -> 93 // Page down
        in 0xEFBE..0xEFC9 -> 131 + id - 0xEFBE // F1..F12
        in 'a'.code..'z'.code -> 29 + id - 'a'.code
        in 'A'.code..'Z'.code -> 29 + id - 'A'.code
        in '0'.code..'9'.code -> 7 + id - '0'.code
        ' '.code -> 62
        ','.code -> 55
        '.'.code -> 56
        '-'.code -> 69
        '='.code -> 70
        '['.code -> 71
        ']'.code -> 72
        '\\'.code -> 73
        ';'.code -> 74
        '\''.code -> 75
        '/'.code -> 76
        '`'.code -> 68
        else -> null
    }
    fun editAction(id: Int, mask: Int): Int? {
        if (mask and (KeyModifier.CONTROL or KeyModifier.META or KeyModifier.SUPER) == 0 ||
            mask and KeyModifier.ALT != 0) return null
        val letter = if (id in 1..26) 'a'.code + id - 1 else id.toChar().lowercaseChar().code
        return when (letter) {
            'a'.code -> 16908319 // android.R.id.selectAll
            'x'.code -> 16908320
            'c'.code -> 16908321
            'v'.code -> 16908322
            else -> null
        }
    }
}
