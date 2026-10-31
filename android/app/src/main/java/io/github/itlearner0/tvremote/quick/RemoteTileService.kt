package io.github.itlearner0.tvremote.quick

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tuile des réglages rapides : un toucher allume ou éteint la TV. */
class RemoteTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            label = QuickRemote.currentTv(this@RemoteTileService)?.name ?: "TV"
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        QuickRemote.run(this) { QuickRemote.power(it) }
    }
}
