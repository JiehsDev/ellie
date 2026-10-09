package com.example.jikan.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface UsageStatsDataSource {
    fun hasUsageAccess(): Boolean
    suspend fun queryUsage(windowStartMs: Long, windowEndMs: Long): List<RawAppUsage>
}

class AndroidUsageStatsDataSource(
    private val context: Context,
) : UsageStatsDataSource {
    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun queryUsage(windowStartMs: Long, windowEndMs: Long): List<RawAppUsage> =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess() || windowEndMs <= windowStartMs) return@withContext emptyList()
            val usageStatsManager = context.getSystemService<UsageStatsManager>() ?: return@withContext emptyList()
            val pm = context.packageManager

            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                windowStartMs,
                windowEndMs,
            )
                .orEmpty()
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { it.totalTimeInForeground > 0L }
                .filterNot { stats ->
                    runCatching {
                        val info = pm.getApplicationInfo(stats.packageName, 0)
                        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            pm.getLaunchIntentForPackage(stats.packageName) == null
                    }.getOrDefault(true)
                }
                .groupBy({ it.packageName }, { it.totalTimeInForeground })
                .map { (packageName, durations) -> RawAppUsage(packageName, durations.sum()) }
                .sortedByDescending { it.foregroundMs }
                .toList()
        }
}
