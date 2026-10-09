package com.example.jikan.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ProtectionWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        StrictModeManager.reconcile(applicationContext)
        return Result.success()
    }
}
