package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.app.NotificationManager

class AppCleanupService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        // 1. Cancel the playback notification and any other app notifications
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(12345)
        notificationManager.cancelAll()
        
        // 2. Kill the process to completely clear and reset all background tasks, players, or coroutines
        android.os.Process.killProcess(android.os.Process.myPid())
        
        stopSelf()
    }
}
