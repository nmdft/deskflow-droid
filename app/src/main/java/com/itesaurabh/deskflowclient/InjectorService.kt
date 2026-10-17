// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.graphics.Point
import android.view.Display
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.net.Socket
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The accessibility backend, and the host for the socket thread. An enabled AccessibilityService
 * is already system-bound and long-lived, so there is no foreground service here.
 *
 * Mouse gestures and editor-scoped keyboard input. Android 13+ uses the accessibility input
 * connection; older versions use editable nodes. Neither path replaces the default IME or
 * provides arbitrary system-wide hardware-keyboard injection.
 */
class InjectorService : AccessibilityService(), Injector {

    companion object {
        private const val TAG = "InjectorService"

        /** How long each drag segment lasts. Short keeps the cursor responsive. */
        private const val SEGMENT_MS = 40L
        private const val WHEEL_MS = 100L

        /** Screen pixels travelled per wheel notch (120 units). */
        private const val WHEEL_PX_PER_NOTCH = 220f

        @Volatile
        var instance: InjectorService? = null
            private set

        /**
         * A connect requested before the service had bound. The system restarts an enabled service
         * in a fresh process, and the activity can reach Connect first.
         */
        @Volatile
        var pendingConnect: PendingConnect? = null

        /**
         * How long a burst of keystrokes keeps using the shadow buffer before re-reading the
         * focused field.
         */
        private const val RESYNC_MS = 400L

        /** Shift_L..Hyper_R, plus the three lock keys. Sent as key presses, meaningless to us. */
        private val MODIFIER_KEY_IDS = (0xEFE1..0xEFEE) + listOf(0xEF14, 0xEF7E, 0xEF7F)
    }

    data class PendingConnect(val host: String, val port: Int, val name: String, val useTls: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private var client: DeskflowClient? = null
    private lateinit var screenWake: ScreenWake
    private lateinit var clipboardSync: ClipboardSync
    private var accessibilityKeyboard: AccessibilityKeyboard? = null
    private var keyboardActive = false

    private lateinit var windows: WindowManager
    private var cursor: CursorView? = null
    private lateinit var displays: DisplayManager
    @Volatile
    private var pointerSpace = PointerSpace(1, 1, 0, -1)
    private var logicalX = 0f
    private var logicalY = 0f
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = Unit
        override fun onDisplayRemoved(id: Int) = Unit
        override fun onDisplayChanged(id: Int) { if (id == Display.DEFAULT_DISPLAY) refreshPointerSpace() }
    }

    override val screenWidth get() = pointerSpace.width
    override val screenHeight get() = pointerSpace.height
    override fun screenSize(): Pair<Int, Int> = pointerSpace.let { it.width to it.height }

    // Actual display coordinates, updated in input order on the main thread.
    @Volatile
    private var cx = 0f

    @Volatile
    private var cy = 0f

    // Shadow of the focused text field, main thread only. See syncBuffer.
    private var editNode: AccessibilityNodeInfo? = null
    private var editStamp = 0L
    private var bufText = ""
    private var bufStart = 0
    private var bufEnd = 0

    // Gesture state, main thread only.
    private var stroke: StrokeDescription? = null
    private var inFlight = false
    private var pendingUp = false
    private var lastX = 0f
    private var lastY = 0f
    private var jitter = 1f
    private var topDrag = false
    private var topDragConsumed = false
    private var topStartX = 0f
    private var topStartY = 0f

    override fun onServiceConnected() {
        instance = this
        screenWake = ScreenWake(this)
        clipboardSync = ClipboardSync(this) { client?.sendClipboard(it) }
        clipboardSync.start()
        if (Build.VERSION.SDK_INT >= 33) {
            serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR }
            accessibilityKeyboard = AccessibilityKeyboard(this, clipboardSync)
        }
        restoreLegacyOrientation(this)
        displays = getSystemService(DisplayManager::class.java)
        windows = getSystemService(WindowManager::class.java)
        displays.registerDisplayListener(displayListener, main)
        addOverlay()
        refreshPointerSpace()
        setCursor(screenWidth / 2f, screenHeight / 2f)
        Logs.add("accessibility service enabled, screen ${screenWidth}x$screenHeight")
        ClientState.report("Ready to connect")

        pendingConnect?.let {
            pendingConnect = null
            Logs.add("service bound, running the queued connect")
            connect(it.host, it.port, it.name, it.useTls)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::windows.isInitialized) return
        refreshPointerSpace()
    }

    override fun onDestroy() {
        Logs.add("accessibility service stopped")
        disconnect()
        clipboardSync.stop()
        screenWake.close()
        removeOverlay()
        displays.unregisterDisplayListener(displayListener)
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Focus or window moved: whatever we thought we were editing is no longer it.
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> editNode = null
        }
    }

    override fun onInterrupt() = Unit

    // -- connection ------------------------------------------------------------------------

    fun connect(host: String, port: Int, name: String, useTls: Boolean) {
        disconnect()
        KeepAliveService.start(this)
        val app = applicationContext
        val secure = if (useTls) { socket: Socket -> Tls.wrap(app, socket, host, port) } else null
        client = DeskflowClient(
            host = host,
            port = port,
            clientName = name,
            injector = this,
            secure = secure
        ).also { it.start() }
    }

    fun disconnect() {
        pendingConnect = null
        if (::clipboardSync.isInitialized) clipboardSync.resetSession()
        client?.disconnect()
        client = null
        KeepAliveService.stop(this)
    }

    val isConnected: Boolean
        get() = client?.isAlive == true

    // -- cursor overlay --------------------------------------------------------------------

    fun setPointerMode(mode: Int, reversed: Boolean) {
        require(mode in -1..1)
        getSharedPreferences("deskflow", Context.MODE_PRIVATE).edit()
            .putInt("pointer_mode", mode).putBoolean("pointer_reversed", reversed).apply()
        refreshPointerSpace()
    }

    private fun refreshPointerSpace() {
        val display = displays.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        val prefs = getSharedPreferences("deskflow", Context.MODE_PRIVATE)
        val next = PointerSpace(size.x, size.y, display.rotation,
            prefs.getInt("pointer_mode", -1), prefs.getBoolean("pointer_reversed", false))
        if (next == pointerSpace) return
        releaseStroke()
        pointerSpace = next
        setCursor(logicalX, logicalY)
        Logs.add("Pointer space ${next.width}x${next.height}; display ${size.x}x${size.y}; transform ${next.turns * 90} degrees")
    }

    private fun addOverlay() {
        if (cursor != null) return
        val view = CursorView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        lp.setFitInsetsTypes(0)
        view.visibility = View.GONE
        windows.addView(view, lp)
        cursor = view
    }

    private fun removeOverlay() {
        cursor?.let { windows.removeView(it) }
        cursor = null
    }

    // -- Injector --------------------------------------------------------------------------

    override fun enter(x: Int, y: Int) {
        main.post {
            screenWake.onMouseEnter()
            keyboardActive = true
            editNode = null
        }
        setCursor(x.toFloat(), y.toFloat())
        main.post { cursor?.visibility = View.VISIBLE }
    }

    override fun leave() {
        main.post { keyboardActive = false; editNode = null }
        main.post {
            releaseStroke()
            cursor?.visibility = View.GONE
        }
    }

    override fun moveTo(x: Int, y: Int) = setCursor(x.toFloat(), y.toFloat())

    override fun moveBy(dx: Int, dy: Int) { main.post { setCursor(logicalX + dx, logicalY + dy) } }

    private fun setCursor(x: Float, y: Float) {
        if (Looper.myLooper() != main.looper) { main.post { setCursor(x, y) }; return }
        val space = pointerSpace
        logicalX = x.coerceIn(0f, (space.width - 1).toFloat())
        logicalY = y.coerceIn(0f, (space.height - 1).toFloat())
        val point = space.toDisplay(logicalX, logicalY)
        cx = point.first
        cy = point.second
        cursor?.moveTo(cx, cy, space.cursorAngle)
        if (topDrag) {
            val dy = cy - topStartY
            if (dy >= 48f * resources.displayMetrics.density && dy > kotlin.math.abs(cx - topStartX)) {
                topDrag = false
                topDragConsumed = true
                if (!performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) Logs.warn("System refused notification shade action")
            }
        } else if (!topDragConsumed) {
            strokeStep()
        }
    }

    override fun mouseDown(button: Int) {
        when (button) {
            1 -> main.post {
                topDragConsumed = false
                topDrag = cy <= 24f * resources.displayMetrics.density
                if (topDrag) {
                    topStartX = cx
                    topStartY = cy
                } else {
                    startStroke()
                }
            }
            3 -> performGlobalAction(GLOBAL_ACTION_BACK)
            else -> Log.d(TAG, "ignoring button $button")
        }
    }

    override fun mouseUp(button: Int) {
        if (button != 1) return
        main.post {
            if (topDragConsumed) {
                topDragConsumed = false
                return@post
            }
            if (topDrag) {
                topDrag = false
                startStroke()
            }
            if (stroke != null) {
                pendingUp = true
                strokeStep()
            }
        }
    }

    override fun wheel(dx: Int, dy: Int) {
        if (dy == 0) return
        main.post { swipe(dy) }
    }

    override fun clipboard(text: String) { main.post { clipboardSync.receive(text) } }

    fun publishClipboard() { clipboardSync.publish() }

    override fun keyDown(id: Int, mask: Int) = keyDown(id, mask, 0)
    override fun keyDown(id: Int, mask: Int, button: Int) {
        main.post {
            if (keyboardActive && !(Build.VERSION.SDK_INT >= 33 && accessibilityKeyboard?.key(id, mask) == true))
                applyKey(KeyId.normalize(id), mask)
        }
    }
    override fun keyUp(id: Int, mask: Int, button: Int) {
        // Editor operations are atomic; forwarded keys are paired down/up immediately.
    }
    override fun keyRepeat(id: Int, mask: Int, button: Int, count: Int) {
        repeat(count.coerceAtMost(32)) { keyDown(id, mask, button) }
    }

    // -- gestures --------------------------------------------------------------------------

    private val gestureCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            inFlight = false
            strokeStep()
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            inFlight = false
            releaseStroke()
        }
    }

    private fun startStroke() {
        if (stroke != null) return
        lastX = cx
        lastY = cy
        pendingUp = false
        val nextX = (lastX + if (lastX < pointerSpace.displayWidth - 1) 1f else -1f)
            .coerceIn(0f, (pointerSpace.displayWidth - 1).toFloat())
        val path = Path().apply {
            moveTo(lastX, lastY)
            lineTo(nextX, lastY)
        }
        lastX = nextX
        val opened = StrokeDescription(path, 0, SEGMENT_MS, true)
        stroke = opened
        dispatch(opened)
    }

    /**
     * Extend, or finish, the stroke opened by the last button-down. Continuing at the same point
     * keeps a held button alive, which is what makes press-and-hold and slow drags survive; the
     * one-pixel alternating jitter exists because StrokeDescription rejects an empty path.
     */
    private fun strokeStep() {
        val current = stroke ?: return
        if (inFlight) return

        var tx = cx
        var ty = cy
        if (tx == lastX && ty == lastY) {
            ty = (ty + jitter).coerceIn(0f, (pointerSpace.displayHeight - 1).toFloat())
            jitter = -jitter
        }
        val path = Path().apply {
            moveTo(lastX, lastY)
            lineTo(tx, ty)
        }

        val next = try {
            current.continueStroke(path, 0, SEGMENT_MS, !pendingUp)
        } catch (e: RuntimeException) {
            Log.w(TAG, "stroke continuation rejected", e)
            releaseStroke()
            return
        }

        lastX = tx
        lastY = ty
        if (pendingUp) {
            stroke = null
            pendingUp = false
        } else {
            stroke = next
        }
        dispatch(next)
    }

    private fun dispatch(s: StrokeDescription) {
        inFlight = true
        dispatchGesture(GestureDescription.Builder().addStroke(s).build(), gestureCallback, main)
    }

    private fun releaseStroke() {
        topDrag = false
        topDragConsumed = false
        stroke = null
        pendingUp = false
    }

    private fun swipe(wheelDelta: Int) {
        // A wheel scroll away from the user moves content up, which is a finger swipe downwards.
        if (stroke != null) return
        val maxY = (pointerSpace.displayHeight - 1).toFloat()
        val distance = wheelDelta / 120f * WHEEL_PX_PER_NOTCH
        val startY = cy.coerceIn(maxY * 0.2f, maxY * 0.8f)
        val endY = (startY + distance).coerceIn(0f, maxY)
        if (startY == endY) return

        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        dispatchGesture(
            GestureDescription.Builder().addStroke(StrokeDescription(path, 0, WHEEL_MS)).build(),
            null,
            main
        )
    }

    // -- keyboard --------------------------------------------------------------------------

    private fun applyKey(id: Int, mask: Int) {
        if (id in MODIFIER_KEY_IDS) return

        if (id == KeyId.ESCAPE) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        if (mask and KeyModifier.SHORTCUT != 0) {
            shortcut(id, mask)
            return
        }

        val node = editableFocus() ?: return
        syncBuffer(node)

        when {
            id == KeyId.RETURN -> node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)

            id in listOf(KeyId.LEFT, KeyId.RIGHT, KeyId.UP, KeyId.DOWN, KeyId.HOME, KeyId.END) -> {
                val (start, end) = TextNavigation.move(bufText, bufStart, bufEnd, id, mask)
                bufStart = start; bufEnd = end; pushSelection(node)
            }

            id == KeyId.BACKSPACE -> {
                if (bufStart == bufEnd) {
                    if (bufStart == 0) return
                    bufStart--
                }
                splice(node, "")
            }

            id == KeyId.DELETE -> {
                if (bufStart == bufEnd) {
                    if (bufEnd == bufText.length) return
                    bufEnd++
                }
                splice(node, "")
            }

            KeyId.isPrintable(id) -> splice(node, String(Character.toChars(id)))

            else -> Logs.warn("dropped key 0x%04x".format(id))
        }
    }

    /**
     * The achievable slice of modifier combos. An AccessibilityService cannot inject key events,
     * so a shortcut only works where it maps onto a node action or a global action.
     *
     * ponytail: everything else is logged and dropped. There is no fix for that inside this
     * backend; the Shizuku Injector is what makes arbitrary shortcuts possible.
     */
    private fun shortcut(id: Int, mask: Int) {
        // Some servers send the control character rather than the letter for Ctrl combos.
        val letter = when (id) {
            in 1..26 -> 'a'.code + id - 1
            in 'A'.code..'Z'.code -> id + ('a'.code - 'A'.code)
            else -> id
        }

        if (mask and (KeyModifier.META or KeyModifier.SUPER) != 0 &&
            letter !in listOf('a'.code, 'c'.code, 'x'.code, 'v'.code)) {
            when (letter) {
                KeyId.TAB -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                'h'.code -> performGlobalAction(GLOBAL_ACTION_HOME)
                else -> Logs.warn("dropped meta+0x%04x".format(id))
            }
            return
        }

        if (mask and KeyModifier.ALT != 0) {
            when (letter) {
                KeyId.TAB -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                else -> Logs.warn("dropped alt+0x%04x".format(id))
            }
            return
        }

        val node = editableFocus() ?: return
        syncBuffer(node)
        when (letter) {
            'a'.code -> {
                bufStart = 0
                bufEnd = bufText.length
                pushSelection(node)
            }

            'c'.code -> {
                if (!node.isPassword) EditorText.selection(bufText, bufStart, bufEnd)?.let { clipboardSync.publishSelection(it) }
                node.performAction(AccessibilityNodeInfo.ACTION_COPY)
            }

            'x'.code -> {
                if (!node.isPassword) EditorText.selection(bufText, bufStart, bufEnd)?.let { clipboardSync.publishSelection(it) }
                node.performAction(AccessibilityNodeInfo.ACTION_CUT)
                editNode = null // the field's text changed underneath us
            }

            'v'.code -> {
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                editNode = null
            }

            else -> Logs.warn("dropped ctrl+0x%04x".format(id))
        }
    }

    private fun editableFocus(): AccessibilityNodeInfo? {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null || !node.isEditable) {
            Logs.warn("no editable field focused")
            return null
        }
        return node
    }

    /**
     * Re-reading the node's text on every keystroke is what lost characters: ACTION_SET_TEXT is an
     * async round-trip to the other process, so a key arriving before the previous one landed read
     * stale text and clobbered it. Instead we keep a shadow of the field and only re-read when the
     * focus moved or the user has had a quiet moment to edit it themselves.
     *
     * ponytail: within RESYNC_MS we win any conflict, so a fast burst also overwrites autocorrect.
     * Sync on the text-changed event instead if that ever bites.
     */
    private fun syncBuffer(node: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        if (node == editNode && now - editStamp < RESYNC_MS) {
            editStamp = now
            return
        }

        editNode = node
        editStamp = now
        bufText = if (isShowingHint(node)) "" else node.text?.toString() ?: ""
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (start in 0..bufText.length && end in 0..bufText.length) {
            bufStart = minOf(start, end)
            bufEnd = maxOf(start, end)
        } else {
            bufStart = bufText.length
            bufEnd = bufText.length
        }
    }

    /**
     * An empty field reports its placeholder as its text, so without this the first keystroke gets
     * appended to the placeholder and the whole thing is written back as the value; typing "bing"
     * into a URL bar lands "Search Brave or type URLbing".
     *
     * The flag is what TextView sets; the equality check covers fields that fill in hintText but
     * leave the flag alone.
     */
    private fun isShowingHint(node: AccessibilityNodeInfo): Boolean {
        if (node.isShowingHintText) return true
        val hint = node.hintText ?: return false
        return hint.isNotEmpty() && hint.toString() == node.text?.toString()
    }

    private fun splice(node: AccessibilityNodeInfo, typed: String) {
        bufText = bufText.substring(0, bufStart) + typed + bufText.substring(bufEnd)
        bufStart += typed.length
        bufEnd = bufStart

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, bufText)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Logs.warn("ACTION_SET_TEXT refused")
            editNode = null
            return
        }
        pushSelection(node)
    }

    private fun pushSelection(node: AccessibilityNodeInfo) {
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, bufStart)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, bufEnd)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }
}

/** Full-screen, untouchable overlay that draws the arrow wherever the server put the cursor. */
private class CursorView(context: Context) : View(context) {
    private var px = 0f
    private var py = 0f
    private var angle = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val arrow = Path()
    private val screenLocation = IntArray(2)

    fun moveTo(x: Float, y: Float, rotation: Float) {
        angle = rotation
        px = x
        py = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Gesture coordinates are screen pixels; the overlay canvas has a local origin.
        getLocationOnScreen(screenLocation)
        canvas.save()
        canvas.translate(-screenLocation[0].toFloat(), -screenLocation[1].toFloat())
        canvas.rotate(angle, px, py)
        arrow.reset()
        arrow.moveTo(px, py)
        arrow.lineTo(px, py + 34f)
        arrow.lineTo(px + 9f, py + 26f)
        arrow.lineTo(px + 15f, py + 39f)
        arrow.lineTo(px + 22f, py + 36f)
        arrow.lineTo(px + 16f, py + 23f)
        arrow.lineTo(px + 26f, py + 22f)
        arrow.close()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
        canvas.restore()
    }
}
