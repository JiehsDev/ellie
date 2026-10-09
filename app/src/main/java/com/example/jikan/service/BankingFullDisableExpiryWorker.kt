package com.example.jikan.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BankingFullDisableExpiryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        PauseManager.handleBankingReminder(applicationContext)
        return Result.success()
    }
}
