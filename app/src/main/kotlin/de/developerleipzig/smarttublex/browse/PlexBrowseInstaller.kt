package de.developerleipzig.smarttublex.browse

import android.content.Context

/**
 * Compatibility façade — content menus are installed via [ContentBrowseInstaller].
 */
object PlexBrowseInstaller {
    fun ensureInstalled(context: Context) {
        ContentBrowseInstaller.ensureInstalled(context)
    }

    fun refresh(context: Context) {
        ContentBrowseInstaller.refresh(context)
    }
}
