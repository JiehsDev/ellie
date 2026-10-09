package com.example.jikan.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == PauseManager.ACTION_TOGGLE) {
            context.startActivity(PauseManager.bankingModeIntent(context.applicationContext))
        }
    }
}
