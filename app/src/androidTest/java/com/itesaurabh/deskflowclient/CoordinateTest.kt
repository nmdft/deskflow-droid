// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.pm.ActivityInfo
import android.widget.EditText
import android.accessibilityservice.AccessibilityService
import android.widget.Button
import android.widget.FrameLayout
import android.view.Gravity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CoordinateTest {
    @Test fun rotationUpdatesDimensionsAndClicksReachTarget() {
        // MIUI blocks background activity launches: bring the app foreground via adb during this pause.
        Thread.sleep(6000)
        android.util.Log.i("CoordinateTest", "launch")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            android.util.Log.i("CoordinateTest", "launched")
            val deadline = System.currentTimeMillis() + 10000
            while (InjectorService.instance == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            val service = requireNotNull(InjectorService.instance) { "Enable Fix accessibility service first" }
            val prefs=service.getSharedPreferences("deskflow", android.content.Context.MODE_PRIVATE)
            val oldMode=prefs.getInt("pointer_mode", -1)
            val oldReverse=prefs.getBoolean("pointer_reversed", false)
            scenario.onActivity { service.setPointerMode(-1, false) }
            try {
                for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)) {
                    android.util.Log.i("CoordinateTest", "rotate $orientation")
                    scenario.onActivity { it.requestedOrientation = orientation }
                    Thread.sleep(1800)
                    val clicked = CountDownLatch(1)
                    var targetX = 0
                    var targetY = 0
                    scenario.onActivity { activity ->
                        val target = Button(activity).apply {
                            text = "Coordinate verification"
                            setOnClickListener { clicked.countDown() }
                        }
                        val frame = FrameLayout(activity)
                        frame.addView(target, FrameLayout.LayoutParams(80, 80, Gravity.CENTER))
                        activity.setContentView(frame)
                        target.post {
                            val origin = IntArray(2)
                            target.getLocationOnScreen(origin)
                            targetX = origin[0] + target.width / 2
                            targetY = origin[1] + target.height / 2
                        }
                    }
                    Thread.sleep(300)
                    android.util.Log.i("CoordinateTest", "click $targetX,$targetY dims ${service.screenWidth}x${service.screenHeight}")
                    val portrait = orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    assertEquals(if (portrait) 1080 else 2400, service.screenWidth)
                    assertEquals(if (portrait) 2400 else 1080, service.screenHeight)
                    service.enter(targetX, targetY)
                    service.mouseDown(1)
                    Thread.sleep(100)
                    service.mouseUp(1)
                    assertTrue("Click missed orientation $orientation", clicked.await(3, TimeUnit.SECONDS))
                    lateinit var field: EditText
                    scenario.onActivity { activity ->
                        field = EditText(activity)
                        activity.setContentView(field)
                        field.requestFocus()
                    }
                    Thread.sleep(400)
                    service.keyDown('a'.code, 0)
                    service.keyDown('B'.code, KeyModifier.SHIFT)
                    for (key in 0xEFB0..0xEFB9) service.keyDown(key, 0)
                    service.keyDown(0xEFAE, 0)
                    Thread.sleep(500)
                    scenario.onActivity { assertEquals("aB0123456789.", field.text.toString()) }
                    service.keyDown('A'.code, KeyModifier.SUPER)
                    service.keyDown('中'.code, 0)
                    Thread.sleep(500)
                    scenario.onActivity { assertEquals("中", field.text.toString()) }
                    service.keyDown(KeyId.BACKSPACE, 0)
                    Thread.sleep(300)
                    scenario.onActivity { assertEquals("", field.text.toString()) }

                }
                service.enter(service.screenWidth / 2, 1)
                Thread.sleep(100)
                service.mouseDown(1)
                Thread.sleep(100)
                service.moveTo(service.screenWidth / 2, 300)
                Thread.sleep(800)
                service.mouseUp(1)
                assertEquals("com.android.systemui", service.rootInActiveWindow?.packageName?.toString())
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } finally {
                service.leave()
                scenario.onActivity {
                    service.setPointerMode(oldMode, oldReverse)
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }
}
