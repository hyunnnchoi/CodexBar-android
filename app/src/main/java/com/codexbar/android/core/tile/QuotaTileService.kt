package com.codexbar.android.core.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuotaTileService : TileService() {

    @Inject
    lateinit var prefsManager: EncryptedPrefsManager

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivityAndCollapse(intent)
    }

    private fun updateTile() {
        val tile = qsTile ?: return

        val hasAnyCredential = AiService.entries.any { prefsManager.hasCredential(it) }

        if (!hasAnyCredential) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "CodexBar"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Tap to set up"
            }
            tile.updateTile()
            return
        }

        // Check connectivity is handled at the WorkManager level
        tile.state = Tile.STATE_ACTIVE
        tile.label = "CodexBar"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = buildSummarySubtitle()
        }
        tile.updateTile()
    }

    private fun buildSummarySubtitle(): String {
        // Summary will be updated by WorkManager after fetch
        val services = AiService.entries.filter { prefsManager.hasCredential(it) }
        return services.joinToString(" | ") { it.displayName }
    }
}
