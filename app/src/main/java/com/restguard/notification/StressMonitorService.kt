package com.restguard.notification

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps the stress notification alive.
 *
 * This service exists purely to maintain the persistent notification.
 * Actual work is done by StressMonitorWorker via WorkManager.
 * The service ensures the notification isn't killed by the OS.
 */
@AndroidEntryPoint
class StressMonitorService : Service() {

    @Inject
    lateinit var notificationManager: StressNotificationManager

    override fun onCreate() {
        super.onCreate()
        val notification = notificationManager.buildForegroundNotification()
        ServiceCompat.startForeground(
            this,
            StressNotificationManager.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancel()
    }
}
