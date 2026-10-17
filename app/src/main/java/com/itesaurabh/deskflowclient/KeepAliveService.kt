package com.itesaurabh.deskflowclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

/**
 * Holds nothing and does nothing; its only job is the ongoing notification, which is what keeps
 * this process alive while connected.
 *
 * The accessibility service lives in this same process and cannot be a foreground service itself.
 * Without something holding the process up, an aggressive OEM task killer takes it down, and some
 * ROMs then disable the accessibility grant rather than rebinding it, which is what makes the
 * permission look like it "resets" on every restart.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            InjectorService.instance?.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }

        // Keeping the process alive is an enhancement, not the feature. Foreground-service policy
        // is version and OEM dependent, so a refusal must not take the connection down with it.
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification)
            }
            Logs.add("foreground service running")
        } catch (e: Exception) {
            Logs.add("could not start the foreground service: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Connection", NotificationManager.IMPORTANCE_LOW)
        )

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, KeepAliveService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Deskflow Droid")
            .setContentText("Connected to your desktop")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Disconnect",
                    disconnect
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "connection"
        private const val ID = 1
        private const val ACTION_DISCONNECT = "com.itesaurabh.deskflowclient.DISCONNECT"

        fun start(context: Context) {
            try {
                Logs.add("starting the foreground service")
                context.startForegroundService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses background starts outright. Connect anyway.
                Logs.add("could not start the foreground service: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                Logs.add("could not stop the foreground service: ${e.javaClass.simpleName}")
            }
        }
    }
}
