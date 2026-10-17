// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

/** Deskflow KeyIDs -> USB HID usages. State is keyed by the server's physical button ID. */
class HidKeyboard(private val send: (ByteArray) -> Unit) {
    private val held = linkedMapOf<Int, Int>()
    private val consumers = linkedMapOf<Int, Int>()
    private var modifiers = 0
    private var locks = 0
    fun initializeLocks(leds: Int) { locks = leds and 7 }

    fun key(id: Int, mask: Int, button: Int, down: Boolean): Boolean {
        val token = if (button != 0) button else id
        modifiers = mask
        if (!down) {
            held.remove(token)
            consumers.remove(token)
            // Key-up masks can describe the state before releasing a modifier.
            modifiers = modifiers and modifierMask(id).inv()
            emit()
            return true
        }
        val symbol = if (id in 1..26 && mask and KeyModifier.CONTROL != 0) id + 96 else id
        val usage = usage(symbol)
        val consumer = consumer(id)
        if (usage == null && consumer == null) return false
        val lockBit = when(usage) { 83 -> 1; 57 -> 2; 71 -> 4; else -> 0 }
        if (lockBit != 0) {
            if (!held.containsKey(token)) locks = locks xor lockBit
        } else {
            // macOS can send KP_2 with no NumLock modifier. The symbol is authoritative.
            val numLock = when {
                id in 0xEFB0..0xEFB9 || id == 0xEFAE -> 1
                id in 0xEF95..0xEF9F -> 0
                mask and 0x2000 != 0 -> 1
                else -> locks and 1
            }
            val desired = numLock or
                (if(mask and 0x1000 != 0) 2 else 0) or (if(mask and 0x4000 != 0) 4 else 0)
            for ((bit, lockUsage) in listOf(1 to 83, 2 to 57, 4 to 71)) {
                if ((locks xor desired) and bit != 0) {
                    held[-lockUsage] = lockUsage; emit()
                    held.remove(-lockUsage); emit()
                }
            }
            locks = desired
        }
        if (usage != null) held[token] = usage
        if (consumer != null) consumers[token] = consumer
        modifiers = modifiers or modifierMask(id)
        emit()
        return true
    }

    fun reset() {
        held.clear(); consumers.clear(); modifiers = 0; emit()
    }

    private fun emit() {
        val report = ByteArray(30)
        report[0] = 1
        fun press(usage: Int) {
            val index = 1 + usage / 8
            report[index] = (report[index].toInt() or (1 shl (usage % 8))).toByte()
        }
        held.values.forEach(::press)
        // Retain modifiers that were already held when the cursor entered the phone.
        for ((mask, usage) in listOf(1 to 0xe1, 2 to 0xe0, 4 to 0xe2, 8 to 0xe3, 16 to 0xe3, 32 to 0xe6)) {
            if (modifiers and mask != 0 && held.values.none { it == usage || it == usage + 4 }) press(usage)
        }
        send(report)
        val consumer = consumers.values.lastOrNull() ?: 0
        send(byteArrayOf(2, consumer.toByte(), (consumer shr 8).toByte()))
    }

    companion object {
        private fun modifierMask(id: Int): Int = when(id) {
            0xEFE1, 0xEFE2 -> 1
            0xEFE3, 0xEFE4 -> 2
            0xEFE9, 0xEFEA -> 4
            0xEFE7, 0xEFE8 -> 8
            0xEFEB, 0xEFEC -> 16
            else -> 0
        }
        fun consumer(id: Int): Int? = when(id) {
            0xE0AD -> 0xe2; 0xE0AE -> 0xea; 0xE0AF -> 0xe9
            0xE0B0 -> 0xb5; 0xE0B1 -> 0xb6; 0xE0B2 -> 0xb7; 0xE0B3 -> 0xcd
            0xE0B8 -> 0x70; 0xE0B9 -> 0x6f
            else -> null
        }
        fun usage(id: Int): Int? {
            if(id in 'a'.code..'z'.code) return id - 'a'.code + 4
            if(id in 'A'.code..'Z'.code) return id - 'A'.code + 4
            if(id in '1'.code..'9'.code) return id - '1'.code + 30
            if(id in 0xEFBE..0xEFC9) return id - 0xEFBE + 58 // F1..F12
            if(id in 0xEFCA..0xEFD5) return id - 0xEFCA + 104 // F13..F24
            if(id in 0xEFB1..0xEFB9) return id - 0xEFB1 + 89
            val plain="-=[]\\;'`,./"
            val shifted="_+{}|:\"~<>?"
            val codes=intArrayOf(45,46,47,48,49,51,52,53,54,55,56)
            plain.indexOf(id.toChar()).takeIf { id < 128 && it >= 0 }?.let { return codes[it] }
            shifted.indexOf(id.toChar()).takeIf { id < 128 && it >= 0 }?.let { return codes[it] }
            "!@#$%^&*()".indexOf(id.toChar()).takeIf { id < 128 && it >= 0 }?.let { return 30 + it }
            return when(id) {
                '0'.code -> 39; ' '.code, 0xEF80 -> 44
                0xEF0D,0xEF0A -> 40; 0xEF1B -> 41; 0xEF08 -> 42; 0xEF09,0xEF89,0xEE20 -> 43
                0xEFE5,0xEFE6 -> 57; 0xEF61,0xEF15 -> 70; 0xEF14 -> 71; 0xEF13 -> 72
                0xEF63 -> 73; 0xEF50 -> 74; 0xEF55 -> 75
                0xEFFF -> 76; 0xEF57 -> 77; 0xEF56 -> 78
                0xEF53 -> 79; 0xEF51 -> 80; 0xEF54 -> 81; 0xEF52 -> 82
                0xEF7F -> 83; 0xEFAF -> 84; 0xEFAA -> 85; 0xEFAD -> 86; 0xEFAB -> 87; 0xEF8D -> 88
                0xEF95 -> 95; 0xEF96 -> 92; 0xEF97 -> 96; 0xEF98 -> 94; 0xEF99 -> 90
                0xEF9A -> 97; 0xEF9B -> 91; 0xEF9C -> 89; 0xEF9D -> 93
                0xEFB0,0xEF9E -> 98; 0xEFAE,0xEF9F -> 99; 0xEF67 -> 101; 0xEFBD -> 103; 0xEFAC -> 133
                0xEFE3 -> 224; 0xEFE1 -> 225; 0xEFE9 -> 226; 0xEFE7,0xEFEB -> 227
                0xEFE4 -> 228; 0xEFE2 -> 229; 0xEFEA -> 230; 0xEFE8,0xEFEC -> 231
                else -> null
            }
        }
    }
}
