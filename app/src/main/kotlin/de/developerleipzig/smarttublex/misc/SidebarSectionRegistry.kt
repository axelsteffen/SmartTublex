package de.developerleipzig.smarttublex.misc

import android.content.Context
import de.developerleipzig.immichapi.prefs.ImmichPrefs
import de.developerleipzig.plexapi.prefs.PlexPrefs
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import de.developerleipzig.smarttublex.errors.ImmichSignInPlaceholder
import de.developerleipzig.smarttublex.errors.PlexBrowseErrorHandler
import de.developerleipzig.smarttublex.errors.PlexSignInPlaceholder

/**
 * Extension point for sidebar section ids beyond upstream YouTube categories.
 * Reserved ids: [TYPE_PLEX] (100), [TYPE_IMMICH] (101).
 */
object SidebarSectionRegistry {
    const val TYPE_PLEX: Int = 100
    const val TYPE_IMMICH: Int = 101

    /** Hardcoded — app module resources are not merged into the wrapped TV APK. */
    const val TITLE_PLEX: String = "Plex"
    const val TITLE_IMMICH: String = "Immich"

    fun isExtraSection(sectionId: Int): Boolean = sectionId >= TYPE_PLEX

    fun isPlexReady(context: Context): Boolean {
        val prefs = PlexPrefs.instance(context)
        return prefs.authToken != null && prefs.selectedServer != null
    }

    fun isImmichReady(context: Context): Boolean {
        val prefs = ImmichPrefs.instance(context)
        return !prefs.serverUrl.isNullOrEmpty()
            && !prefs.apiKey.isNullOrEmpty()
            && prefs.isValidated
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

    fun createImmichSection(context: Context): BrowseSection {
        if (!MediaSourceRegistry.isImmichEnabled()) {
            return BrowseSection(
                TYPE_IMMICH,
                TITLE_IMMICH,
                BrowseSection.TYPE_ERROR,
                R.drawable.icon_playlist,
                false,
                ImmichSignInPlaceholder(context, ImmichSignInPlaceholder.Mode.DISABLED)
            )
        }
        if (isImmichReady(context)) {
            // WRAPPER: 3d — rows via ImmichBrowseInstaller mRowMapping
            return BrowseSection(
                TYPE_IMMICH,
                TITLE_IMMICH,
                BrowseSection.TYPE_ROW,
                R.drawable.icon_playlist,
                false
            )
        }
        return BrowseSection(
            TYPE_IMMICH,
            TITLE_IMMICH,
            BrowseSection.TYPE_ERROR,
            R.drawable.icon_playlist,
            false,
            ImmichSignInPlaceholder(context, ImmichSignInPlaceholder.Mode.SIGN_IN)
        )
    }
}
