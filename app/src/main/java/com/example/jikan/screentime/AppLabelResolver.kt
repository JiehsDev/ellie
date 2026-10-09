package com.example.jikan.screentime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppLabelResolver(private val context: Context) {
    suspend fun labelsFor(packageNames: Collection<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        packageNames.associateWith { packageName ->
            runCatching {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrElse { packageName }
        }
    }
}
