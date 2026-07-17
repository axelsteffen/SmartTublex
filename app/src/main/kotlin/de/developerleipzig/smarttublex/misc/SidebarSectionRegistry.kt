package de.developerleipzig.smarttublex.misc

import android.content.Context
import com.liskovsoft.plexapi.prefs.PlexPrefs
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import de.developerleipzig.smarttublex.errors.PlexBrowseErrorHandler
import de.developerleipzig.smarttublex.errors.PlexSignInPlaceholder

/**
 * Extension point for sidebar section ids beyond upstream YouTube categories.
 * Reserved ids start at [TYPE_PLEX] (100).
 */
object SidebarSectionRegistry {
    const val TYPE_PLEX: Int = 100

    /** Hardcoded — app module resources are not merged into the wrapped TV APK. */
    const val TITLE_PLEX: String = "Plex"

    fun isExtraSection(sectionId: Int): Boolean = sectionId >= TYPE_PLEX

    fun isPlexReady(context: Context): Boolean {
        val prefs = PlexPrefs.instance(context)
        return prefs.authToken != null && prefs.selectedServer != null
    }

    fun createPlexSection(context: Context): BrowseSection {
        if (!MediaSourceRegistry.isPlexEnabled()) {
            return BrowseSection(
                TYPE_PLEX,
                TITLE_PLEX,
                BrowseSection.TYPE_ERROR,
                R.drawable.icon_playlist,
                false,
                PlexSignInPlaceholder(context, PlexSignInPlaceholder.Mode.DISABLED)
            )
        }
        if (isPlexReady(context)) {
            // Sticky offline / load-failed UI until the user taps Retry.
            val sticky = PlexBrowseErrorHandler.stickyMode
            if (sticky != null) {
                return BrowseSection(
                    TYPE_PLEX,
                    TITLE_PLEX,
                    BrowseSection.TYPE_ERROR,
                    R.drawable.icon_playlist,
                    false,
                    PlexSignInPlaceholder(context, sticky)
                )
            }
            // WRAPPER: 3c — rows via PlexBrowseInstaller mRowMapping
            return BrowseSection(
                TYPE_PLEX,
                TITLE_PLEX,
                BrowseSection.TYPE_ROW,
                R.drawable.icon_playlist,
                false
            )
        }
        return BrowseSection(
            TYPE_PLEX,
            TITLE_PLEX,
            BrowseSection.TYPE_ERROR,
            R.drawable.icon_playlist,
            false,
            PlexSignInPlaceholder(context, PlexSignInPlaceholder.Mode.SIGN_IN)
        )
    }
}
