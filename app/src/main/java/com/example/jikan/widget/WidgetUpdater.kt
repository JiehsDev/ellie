package com.example.jikan.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Call after anything that changes the wallet balance or streak so the home-screen widget stays live. */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        JikanWidget().updateAll(context)
    }
}
