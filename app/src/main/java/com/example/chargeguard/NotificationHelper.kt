package com.example.chargeguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "chargeguard_alert"
    private const val CHANNEL_MONITORING_ID = "chargeguard_monitoring"
    const val ALERT_NOTIFICATION_ID = 1001
    const val MONITORING_NOTIFICATION_ID = 1002

    // Call this ONCE before sending any notification — like registering a service worker
    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Alert channel — high importance, sound on
        val alertChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
        }

        // Monitoring channel — low importance, silent
        val monitoringChannel = NotificationChannel(
            CHANNEL_MONITORING_ID,
            "ChargeGuard Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current battery % while monitoring"
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(monitoringChannel)
    }

    // The big looping alarm notification — shown when not charging
    fun showAlertNotification(context: Context, batteryPercent: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // "Stop Alert" button action
        val stopIntent = Intent("com.example.chargeguard.STOP_ALERT").apply {
            setPackage(context.packageName)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tapping notification opens app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ Charger not working!")
            .setContentText("Battery at $batteryPercent% — charger connected but not charging")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your charger is connected but the battery is NOT charging. Check your cable, adapter, or charging port.\n\nBattery: $batteryPercent%"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)           // Cannot be swiped away
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop Alert",
                stopPendingIntent
            )
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    // Silent monitoring notification — shows battery % while checking
    fun showMonitoringNotification(context: Context, batteryPercent: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MONITORING_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("ChargeGuard — Checking charger…")
            .setContentText("Battery: $batteryPercent% — verifying charge in 10s")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        manager.notify(MONITORING_NOTIFICATION_ID, notification)
    }

    fun cancelAlertNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ALERT_NOTIFICATION_ID)
    }

    fun cancelMonitoringNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MONITORING_NOTIFICATION_ID)
    }

    fun cancelAllNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    // Looping vibration — called from ChargingWorker
    fun startLoopingVibration(context: Context) {
        val pattern = longArrayOf(0, 500, 200, 500)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    fun stopVibration(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            @Suppress("DEPRECATION")
            vibrator.cancel()
        }
    }
    fun buildMonitoringNotification(context: Context, batteryPercent: Int): android.app.Notification {
        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITORING_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("ChargeGuard — Monitoring")
            .setContentText("Battery: $batteryPercent% — checking every 10s")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    fun updateMonitoringNotification(context: Context, batteryPercent: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MONITORING_NOTIFICATION_ID, buildMonitoringNotification(context, batteryPercent))
    }
}