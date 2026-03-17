package com.codexbar.android.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

object BatteryOptimizationHelper {

    /**
     * Returns true if the app is already exempt from battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Creates an intent to request battery optimization exemption directly.
     * Uses ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS for a one-tap dialog.
     */
    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Fallback: opens the system battery optimization settings page
     * where the user can manually exempt the app.
     */
    fun openBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
