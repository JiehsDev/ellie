package com.example.jikan.onboarding

/**
 * OEM-specific autostart screen a battery-optimization deep link should try
 * first, before falling back to the app's own settings page. See
 * project-context.md "Service survival / OEM battery optimization" — these
 * skins are common in the Philippines and kill background services unless
 * whitelisted, even after the standard Android exemption is granted.
 *
 * No Android framework types here (just package/class name strings) so this
 * stays plain-Kotlin unit testable; the caller builds the actual Intent.
 */
data class AutostartTarget(val packageName: String, val className: String)

object OemBatteryHints {
    fun autostartTargetFor(manufacturer: String): AutostartTarget? {
        return when (manufacturer.trim().lowercase()) {
            "xiaomi" -> AutostartTarget(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            "oppo" -> AutostartTarget(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            )
            "vivo" -> AutostartTarget(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            )
            else -> null
        }
    }

    fun showsAutostartNote(manufacturer: String): Boolean = autostartTargetFor(manufacturer) != null
}
