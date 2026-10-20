package com.orionisli.gh0stra1n

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class OverlayDaemonBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val serviceIntent = Intent(context, Gh0stOverlayService::class.java)
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                    AppLogger.i("OverlayDaemon", "daemon requested after ${intent.action}")
                } catch (e: Throwable) {
                    AppLogger.w("OverlayDaemon", "daemon start rejected: ${e.message}")
                }
            }
        }
    }
}
