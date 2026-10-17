// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.system.Os
import android.system.OsConstants
import android.util.Log
import org.junit.Test

class UhidAccessTest {
    @Test fun reportOrdinaryAppDeviceAccess() {
        try {
            val fd = Os.open("/dev/uhid", OsConstants.O_RDWR, 0)
            Os.close(fd)
            Log.i("UhidAccessCheck", "Ordinary app: UHID open allowed")
        } catch (e: Exception) {
            Log.i("UhidAccessCheck", "Ordinary app: UHID denied: ${e.message}")
        }
    }
}
