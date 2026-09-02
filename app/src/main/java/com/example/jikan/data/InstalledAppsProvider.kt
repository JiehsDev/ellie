package com.example.jikan.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Lists launchable, non-system apps for the onboarding app picker. Relies on
 * a `<queries>` LAUNCHER intent filter in the manifest rather than
 * QUERY_ALL_PACKAGES, so no special Play Store permission declaration is needed.
 */
object InstalledAppsProvider {
    suspend fun listLaunchableApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ownPackage = context.packageName

        pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ownPackage }
            .mapNotNull { packageName ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    InstalledAppInfo(
                        packageName = packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull(),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
