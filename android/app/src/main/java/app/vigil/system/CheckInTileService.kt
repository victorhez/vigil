package app.vigil.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.vigil.MainActivity
import app.vigil.VigilApplication
import app.vigil.ui.components.TimeText

/** Quick Settings tile: swipe down, tap, touch the sensor. The fastest check-in on the phone. */
class CheckInTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        val snapshot = (application as VigilApplication).container.prefs.snapshot
        val now = System.currentTimeMillis() / 1000
        tile.label = "Clock in"
        when {
            snapshot == null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "No vigil yet"
            }
            snapshot.releasedAt != 0L -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = "Released"
            }
            else -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = if (now < snapshot.dueAt) "${TimeText.clock(snapshot.dueAt - now)} left" else "Overdue"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CHECK_IN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
