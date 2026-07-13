package dev.bikram.remember.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R

abstract class QuickCreateTileService : TileService() {
    protected abstract val tileLabelRes: Int
    protected abstract val shortcutAction: String
    protected abstract val requestCode: Int

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.label = getString(tileLabelRes)
            tile.contentDescription = getString(tileLabelRes)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun {
                launchShortcut()
            }
        } else {
            launchShortcut()
        }
    }

    private fun launchShortcut() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = shortcutAction
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            startActivityAndCollapseCompat(intent)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}

class NewNoteTileService : QuickCreateTileService() {
    override val tileLabelRes: Int = R.string.shortcut_new_note_long
    override val shortcutAction: String = MainActivity.ACTION_SHORTCUT_NEW_NOTE
    override val requestCode: Int = 0x514E4F54
}

class NewListTileService : QuickCreateTileService() {
    override val tileLabelRes: Int = R.string.shortcut_new_list_long
    override val shortcutAction: String = MainActivity.ACTION_SHORTCUT_NEW_LIST
    override val requestCode: Int = 0x514C4953
}
