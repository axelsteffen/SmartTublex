package de.developerleipzig.smarttublex.misc

import android.content.Context

/**
 * Ensures Immich (and other wrapper) settings items are in the upstream settings grid.
 * Injection is owned by [SettingsGridInstaller] so Plex + Immich coexist.
 */
object ImmichSettingsInstaller {
    fun ensureInstalled(context: Context) {
        SettingsGridInstaller.ensureInstalled(context)
    }
}
