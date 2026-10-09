package com.example.jikan.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jikan.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PauseManagerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun togglePausesThenResumesLocking() = runBlocking {
        PauseManager.resume(context)

        PauseManager.toggle(context, durationMinutes = 10)
        val pausedExpiry = AppDatabase.getInstance(context).pauseStateDao().get()?.expiryTimestampMs ?: 0L
        assertTrue(PauseStatusCalculator.status(pausedExpiry, System.currentTimeMillis()).isPaused)

        PauseManager.toggle(context, durationMinutes = 10)
        val resumedExpiry = AppDatabase.getInstance(context).pauseStateDao().get()?.expiryTimestampMs ?: 0L
        assertFalse(PauseStatusCalculator.status(resumedExpiry, System.currentTimeMillis()).isPaused)
    }

    @Test
    fun handleExpiryDisablesPauseAutomatically() = runBlocking {
        PauseManager.pause(context, durationMinutes = 10)
        PauseManager.handleExpiry(context)

        val expiry = AppDatabase.getInstance(context).pauseStateDao().get()?.expiryTimestampMs ?: 0L
        assertFalse(PauseStatusCalculator.status(expiry, System.currentTimeMillis()).isPaused)
    }
}
