// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.Context
import android.provider.Settings

/** One-time rollback of v4's system-rotation settings. New installs never change them. */
fun restoreLegacyOrientation(context: Context) {
    val prefs = context.getSharedPreferences("deskflow", Context.MODE_PRIVATE)
    val edit = prefs.edit()
    if (!prefs.contains("pointer_mode") && prefs.contains("orientation")) {
        val old = prefs.getInt("orientation", -1)
        edit.putInt("pointer_mode", when (old) { 0 -> 1; 1 -> 0; else -> -1 })
    }
    if (prefs.contains("rotation_saved_auto") && Settings.System.canWrite(context)) {
        Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, prefs.getInt("rotation_saved_user", 0))
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, prefs.getInt("rotation_saved_auto", 0))
        edit.remove("rotation_saved_auto").remove("rotation_saved_user")
    }
    edit.remove("orientation").apply()
}
