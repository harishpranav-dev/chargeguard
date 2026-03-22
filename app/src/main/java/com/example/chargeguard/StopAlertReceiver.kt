package com.example.chargeguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.chargeguard.STOP_ALERT") {
            NotificationHelper.cancelAllNotifications(context)
            NotificationHelper.stopVibration(context)
        }
    }
}