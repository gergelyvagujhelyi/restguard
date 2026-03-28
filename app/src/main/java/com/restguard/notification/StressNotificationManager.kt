package com.restguard.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.restguard.MainActivity
import com.restguard.R
import com.restguard.domain.model.StressLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the always-running foreground notification.
 *
 * - Color correlates with stress level (green/amber/orange/red)
 * - Updates in-place (same notification ID, no spam)
 * - Shows action button when recommendations are available
 * - Deep-links to dashboard on tap
 */
@Singleton
class StressNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "restguard_stress"
        const val CHANNEL_NAME = "Stress Monitor"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Always-on stress level indicator"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Update the persistent notification with current stress state.
     */
    fun updateNotification(
        stressScore: Int,
        stressLevel: StressLevel,
        pendingRecommendations: Int,
    ) {
        val state = StressNotificationService.buildNotificationState(
            stressScore, stressLevel, pendingRecommendations,
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val color = when (stressLevel) {
            StressLevel.LOW -> 0xFF4CAF50.toInt()       // green
            StressLevel.MODERATE -> 0xFFFFC107.toInt()  // amber
            StressLevel.HIGH -> 0xFFFF9800.toInt()      // orange
            StressLevel.EXTREME -> 0xFFF44336.toInt()   // red
        }

        val priority = when (stressLevel) {
            StressLevel.LOW -> NotificationCompat.PRIORITY_LOW
            StressLevel.MODERATE -> NotificationCompat.PRIORITY_DEFAULT
            StressLevel.HIGH -> NotificationCompat.PRIORITY_HIGH
            StressLevel.EXTREME -> NotificationCompat.PRIORITY_HIGH
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RestGuard — ${stressLevel.label}")
            .setContentText(state.headline)
            .setColor(color)
            .setColorized(true)
            .setPriority(priority)
            .setOngoing(true)
            .setOnlyAlertOnce(!StressNotificationService.shouldHeadsUp(state))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        // Add "View Suggestions" action when recommendations exist
        if (pendingRecommendations > 0) {
            val suggestionsIntent = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    action = StressNotificationService.ACTION_VIEW_SUGGESTIONS
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                R.drawable.ic_notification,
                "View Suggestions ($pendingRecommendations)",
                suggestionsIntent,
            )
        }

        // Add "How are you?" check-in action
        val checkInIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = StressNotificationService.ACTION_CHECK_IN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            R.drawable.ic_notification,
            "How are you?",
            checkInIntent,
        )

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Build a notification suitable for use as a foreground service notification.
     */
    fun buildForegroundNotification() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("RestGuard")
        .setContentText("Monitoring stress levels...")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
