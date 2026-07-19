package de.developerleipzig.smarttublex.misc

import android.content.Context
import de.developerleipzig.immichapi.prefs.ImmichPrefs
import de.developerleipzig.plexapi.prefs.PlexPrefs
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import de.developerleipzig.smarttublex.errors.ImmichSignInPlaceholder
import de.developerleipzig.smarttublex.errors.PlexBrowseErrorHandler
import de.developerleipzig.smarttublex.errors.PlexSignInPlaceholder

/**
 * Sidebar section ids beyond upstream YouTube categories.
 *
 * Content menus replace the former single Plex / Immich entries:
 * Filme / TV-Shows / Merkliste (Plex), Fotos / Alben (Immich).
 */
object SidebarSectionRegistry {
    const val TYPE_MOVIES: Int = 100
    const val TYPE_SHOWS: Int = 101
    const val TYPE_WATCHLIST: Int = 102
    const val TYPE_PHOTOS: Int = 103
    const val TYPE_ALBUMS: Int = 104

    /** Hardcoded — app module resources are not merged into the wrapped TV APK. */
    const val TITLE_MOVIES: String = "Filme"
    const val TITLE_SHOWS: String = "TV-Shows"
    const val TITLE_WATCHLIST: String = "Merkliste"
    const val TITLE_PHOTOS: String = "Fotos"
    const val TITLE_ALBUMS: String = "Alben"

    /** Settings grid labels (not sidebar content menus). */
    const val TITLE_SETTINGS_PLEX: String = "Plex"
    const val TITLE_SETTINGS_IMMICH: String = "Immich"

    /**
     * Icons copied into the decoded APK by [packageWrapperApk]
     * (`android.resource://org.smarttube.beta/drawable/...`).
     */
    private const val PKG = "org.smarttube.beta"
    const val ICON_MOVIES: String = "android.resource://$PKG/drawable/icon_movies"
    const val ICON_SHOWS: String = "android.resource://$PKG/drawable/icon_tv_shows"
    const val ICON_WATCHLIST: String = "android.resource://$PKG/drawable/icon_watchlist"
    const val ICON_PHOTOS: String = "android.resource://$PKG/drawable/icon_photos"
    const val ICON_ALBUMS: String = "android.resource://$PKG/drawable/icon_albums"

    /** All content section ids in sidebar pin order (after Home). */
    val CONTENT_SECTION_IDS: IntArray = intArrayOf(
        TYPE_MOVIES, TYPE_SHOWS, TYPE_WATCHLIST, TYPE_PHOTOS, TYPE_ALBUMS
    )

    fun isExtraSection(sectionId: Int): Boolean = sectionId >= TYPE_MOVIES

    fun isPlexSection(sectionId: Int): Boolean =
        sectionId == TYPE_MOVIES || sectionId == TYPE_SHOWS || sectionId == TYPE_WATCHLIST

    fun isImmichSection(sectionId: Int): Boolean =
        sectionId == TYPE_PHOTOS || sectionId == TYPE_ALBUMS

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

    fun createMoviesSection(context: Context): BrowseSection =
        createPlexBackedSection(context, TYPE_MOVIES, TITLE_MOVIES, ICON_MOVIES)

    fun createShowsSection(context: Context): BrowseSection =
        createPlexBackedSection(context, TYPE_SHOWS, TITLE_SHOWS, ICON_SHOWS)

    fun createWatchlistSection(context: Context): BrowseSection =
        createPlexBackedSection(context, TYPE_WATCHLIST, TITLE_WATCHLIST, ICON_WATCHLIST)

    fun createPhotosSection(context: Context): BrowseSection =
        createImmichBackedSection(context, TYPE_PHOTOS, TITLE_PHOTOS, ICON_PHOTOS)

    fun createAlbumsSection(context: Context): BrowseSection =
        createImmichBackedSection(context, TYPE_ALBUMS, TITLE_ALBUMS, ICON_ALBUMS)

    private fun createPlexBackedSection(
        context: Context,
        id: Int,
        title: String,
        iconUrl: String
    ): BrowseSection {
        if (!MediaSourceRegistry.isPlexEnabled()) {
            return BrowseSection(
                id, title, BrowseSection.TYPE_ERROR, iconUrl, false,
                PlexSignInPlaceholder(context, PlexSignInPlaceholder.Mode.DISABLED)
            )
        }
        if (isPlexReady(context)) {
            val sticky = PlexBrowseErrorHandler.stickyMode
            if (sticky != null) {
                return BrowseSection(
                    id, title, BrowseSection.TYPE_ERROR, iconUrl, false,
                    PlexSignInPlaceholder(context, sticky)
                )
            }
            return BrowseSection(id, title, BrowseSection.TYPE_ROW, iconUrl, false)
        }
        return BrowseSection(
            id, title, BrowseSection.TYPE_ERROR, iconUrl, false,
            PlexSignInPlaceholder(context, PlexSignInPlaceholder.Mode.SIGN_IN)
        )
    }

    private fun createImmichBackedSection(
        context: Context,
        id: Int,
        title: String,
        iconUrl: String
    ): BrowseSection {
        if (!MediaSourceRegistry.isImmichEnabled()) {
            return BrowseSection(
                id, title, BrowseSection.TYPE_ERROR, iconUrl, false,
                ImmichSignInPlaceholder(context, ImmichSignInPlaceholder.Mode.DISABLED)
            )
        }
        if (isImmichReady(context)) {
            return BrowseSection(id, title, BrowseSection.TYPE_ROW, iconUrl, false)
        }
        return BrowseSection(
            id, title, BrowseSection.TYPE_ERROR, iconUrl, false,
            ImmichSignInPlaceholder(context, ImmichSignInPlaceholder.Mode.SIGN_IN)
        )
    }
}
