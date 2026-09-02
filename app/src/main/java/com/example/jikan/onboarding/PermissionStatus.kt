package com.example.jikan.onboarding

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.example.jikan.service.AppLockAccessibilityService

object PermissionStatus {
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppLockAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
