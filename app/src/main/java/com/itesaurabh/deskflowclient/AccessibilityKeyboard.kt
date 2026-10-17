// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod.AccessibilityInputConnection
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi

/** Editor operations, not a system hardware keyboard. All calls run on the main thread. */
@RequiresApi(33)
class AccessibilityKeyboard(private val service: InjectorService, private val clipboard: ClipboardSync) {
    fun key(raw: Int, mask: Int): Boolean {
        val id = KeyId.normalize(raw)
        if (id in 0xEFE1..0xEFEE) return true // Modifier state is carried by each Deskflow event.
        if (id == KeyId.ESCAPE) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); return true }
        if (id == KeyId.TAB && mask and KeyModifier.ALT != 0) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS); return true
        }
        val method = service.inputMethod ?: return false
        val ic = method.currentInputConnection ?: return false
        ImeKeys.editAction(id, mask)?.let { action ->
            // Background clipboard reads are restricted. Forward our copy selection directly;
            // paste still uses the phone's real clipboard, never a stale cached remote value.
            val selected = if (action == android.R.id.copy || action == android.R.id.cut) {
                val info = method.currentInputEditorInfo
                if (info != null && !EditorSecurity.isPassword(info.inputType)) {
                    ic.getSurroundingText(16384, 16384, 0)?.let {
                        EditorText.selection(it.text.toString(), it.selectionStart, it.selectionEnd)
                    }
                } else null
            } else null
            ic.performContextMenuAction(action)
            selected?.let { clipboard.publishSelection(it) }
            return true
        }
        val surrounding = if (id in listOf(KeyId.LEFT, KeyId.RIGHT, KeyId.UP, KeyId.DOWN,
            KeyId.HOME, KeyId.END, KeyId.BACKSPACE, KeyId.DELETE)) ic.getSurroundingText(16384, 16384, 0) else null
        if (surrounding != null && mask and KeyModifier.ALT == 0 &&
            id in listOf(KeyId.LEFT, KeyId.RIGHT, KeyId.UP, KeyId.DOWN, KeyId.HOME, KeyId.END)) {
            val (start, end) = TextNavigation.move(surrounding.text.toString(), surrounding.selectionStart,
                surrounding.selectionEnd, id, mask)
            ic.setSelection(surrounding.offset + start, surrounding.offset + end)
            return true
        }
        if (mask and KeyModifier.SHORTCUT != 0) {
            ImeKeys.code(id)?.let { send(ic, it, mask) }
                ?: Logs.warn("Shortcut not supported by accessibility editor backend")
            return true
        }
        when (id) {
            KeyId.BACKSPACE, KeyId.DELETE -> {
                if (surrounding == null) send(ic, ImeKeys.code(id)!!, mask)
                else if (surrounding.selectionStart != surrounding.selectionEnd) ic.commitText("", 1, null)
                else {
                    val (before, after) = EditorText.deleteUnits(surrounding.text.toString(),
                        surrounding.selectionEnd, id == KeyId.BACKSPACE)
                    ic.deleteSurroundingText(before, after)
                }
            }
            KeyId.RETURN -> {
                val info = method.currentInputEditorInfo
                val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (mask and KeyModifier.SHIFT == 0 && info != null &&
                    info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
                    action !in listOf(EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED)) {
                    ic.performEditorAction(action)
                } else send(ic, KeyEvent.KEYCODE_ENTER, mask)
            }
            else -> if (KeyId.isPrintable(id)) ic.commitText(String(Character.toChars(id)), 1, null)
                else ImeKeys.code(id)?.let { send(ic, it, mask) }
        }
        return true
    }
    private fun send(ic: AccessibilityInputConnection, code: Int, mask: Int) {
        val now = SystemClock.uptimeMillis()
        var meta = 0
        if (mask and KeyModifier.SHIFT != 0) meta = meta or KeyEvent.META_SHIFT_ON
        if (mask and (KeyModifier.CONTROL or KeyModifier.META or KeyModifier.SUPER) != 0)
            meta = meta or KeyEvent.META_CTRL_ON
        if (mask and KeyModifier.ALT != 0) meta = meta or KeyEvent.META_ALT_ON
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }
}

object EditorText {
    fun selection(text: String, start: Int, end: Int): String? {
        if (start !in 0..text.length || end !in 0..text.length || start == end) return null
        return text.substring(minOf(start, end), maxOf(start, end))
    }
    fun deleteUnits(text: String, cursor: Int, backspace: Boolean): Pair<Int, Int> {
        val pos = cursor.coerceIn(0, text.length)
        return if (backspace) {
            (if (pos > 0) pos - text.offsetByCodePoints(pos, -1) else 0) to 0
        } else 0 to (if (pos < text.length) text.offsetByCodePoints(pos, 1) - pos else 0)
    }
}

object EditorSecurity {
    fun isPassword(inputType: Int): Boolean {
        val kind = inputType and 0xf
        val variation = inputType and 0xff0
        return (kind == 1 && variation in listOf(0x80, 0x90, 0xe0)) || (kind == 2 && variation == 0x10)
    }
}
