package org.cyphr.app.tiles

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.AppSettings
import org.cyphr.app.R

class QuickSettingsTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked()) {
            unlockAndRun {
                launchPicker()
            }
        } else {
            launchPicker()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun launchPicker() {
        val intent = Intent(this, ContactPickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        scope.launch {
            val displayName = withContext(Dispatchers.IO) {
                AppSettings.getSelectedContactName(this@QuickSettingsTileService)
            }
            val tile = qsTile ?: return@launch
            if (displayName != null) {
                tile.label = getString(R.string.tile_label_active)
                tile.subtitle = displayName.take(14)
                tile.state = Tile.STATE_ACTIVE
            } else {
                tile.label = getString(R.string.tile_label_inactive)
                tile.subtitle = getString(R.string.tile_no_contact)
                tile.state = Tile.STATE_INACTIVE
            }
            tile.updateTile()
        }
    }
}
