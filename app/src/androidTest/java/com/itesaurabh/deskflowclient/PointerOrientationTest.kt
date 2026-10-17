// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PointerOrientationTest {
    @Test fun mountedLandscapeKeepsDimensionsAndTransformsClicksWithoutRotatingApps() {
        Thread.sleep(6000)
        val deadline=System.currentTimeMillis()+10000
        while(InjectorService.instance==null && System.currentTimeMillis()<deadline) Thread.sleep(100)
        val service=requireNotNull(InjectorService.instance)
        assertFalse("Unlock the phone before input testing", service.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        val prefs=service.getSharedPreferences("deskflow",Context.MODE_PRIVATE)
        val original=prefs.getInt("pointer_mode",-1)
        val reversed=prefs.getBoolean("pointer_reversed",false)
        val resolver=service.contentResolver
        val oldAuto=Settings.System.getInt(resolver,Settings.System.ACCELEROMETER_ROTATION,0)
        val oldUser=Settings.System.getInt(resolver,Settings.System.USER_ROTATION,0)
        // The instrumentation runner owns final activity cleanup; MIUI may pause this
        // activity during the last rotation, so restoration must not require a resumed Activity.
        ActivityScenario.launch(MainActivity::class.java).let { scenario ->
            try {
                scenario.onActivity { service.setPointerMode(1,false) }
                for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT)) {
                    scenario.onActivity {
                        it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        it.requestedOrientation=orientation
                    }
                    Thread.sleep(1400)
                    val rotation=service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation
                    assertEquals(2400,service.screenWidth)
                    assertEquals(1080,service.screenHeight)
                    val expected=when(rotation) {
                        0 -> 679f to 1000f
                        1 -> 1000f to 400f
                        2 -> 400f to 1399f
                        else -> 1399f to 679f
                    }
                    val clicked=CountDownLatch(1)
                    val released=CountDownLatch(1)
                    var x=-1f; var y=-1f; var endX=-1f; var endY=-1f
                    scenario.onActivity { activity ->
                        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        val view=View(activity)
                        view.setOnTouchListener { _,event ->
                            if(event.action==MotionEvent.ACTION_DOWN) {x=event.rawX;y=event.rawY;clicked.countDown()}
                            if(event.action==MotionEvent.ACTION_UP) {endX=event.rawX;endY=event.rawY;released.countDown()}
                            true
                        }
                        activity.setContentView(view)
                    }
                    Thread.sleep(150)
                    service.enter(990,400)
                    service.moveBy(10,0)
                    Thread.sleep(100)
                    service.mouseDown(1);Thread.sleep(100)
                    service.moveBy(40,0);Thread.sleep(120);service.mouseUp(1)
                    assertTrue("No click at rotation $rotation",clicked.await(3,TimeUnit.SECONDS))
                    assertEquals(expected.first,x,2f);assertEquals(expected.second,y,2f)
                    assertTrue("Drag must finish",released.await(3,TimeUnit.SECONDS))
                    val delta=when(rotation) { 0 -> 0f to 40f; 1 -> 40f to 0f; 2 -> 0f to -40f; else -> -40f to 0f }
                    assertEquals(expected.first+delta.first,endX,2f)
                    assertEquals(expected.second+delta.second,endY,2f)
                    assertEquals(oldAuto,Settings.System.getInt(resolver,Settings.System.ACCELEROMETER_ROTATION))
                    assertEquals(oldUser,Settings.System.getInt(resolver,Settings.System.USER_ROTATION))
                    android.util.Log.i("PointerCheck", "Movement, click and drag passed for display rotation $rotation")
                }
            } finally {
                service.leave()
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    service.setPointerMode(original,reversed)
                }
            }
        }
    }
}
