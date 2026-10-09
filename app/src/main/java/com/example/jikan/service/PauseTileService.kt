package com.example.jikan.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.jikan.data.AppDatabase
import com.example.jikan.onboarding.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PauseTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        CoroutineScope(Dispatchers.Main).launch {
            val bankingActive = kotlinx.coroutines.withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).settingsDao().get()?.bankingModeActive ?: false
            }
            val protectionEnabled = PermissionStatus.isAccessibilityServiceEnabled(applicationContext)
            val intent = if (bankingActive && !protectionEnabled) {
                PauseManager.openAccessibilitySettingsIntent()
            } else {
                PauseManager.bankingModeIntent(applicationContext)
            }
            startCollapsed(intent)
        }
    }

    private fun startCollapsed(intent: android.content.Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        CoroutineScope(Dispatchers.Main).launch {
            val bankingActive = kotlinx.coroutines.withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).settingsDao().get()?.bankingModeActive ?: false
            }
            val protectionEnabled = PermissionStatus.isAccessibilityServiceEnabled(applicationContext)
            val tile = qsTile ?: return@launch
            if (bankingActive && !protectionEnabled) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Re-enable"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Banking Mode"
            }
            tile.updateTile()
        }
    }

    companion object {
        fun requestTileRefresh(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(
                    context,
                    ComponentName(context, PauseTileService::class.java),
                )
            }
        }
    }
}
