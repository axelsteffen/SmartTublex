package de.developerleipzig.smarttublex.browse

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.errors.PlexBrowseErrorHandler
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import de.developerleipzig.smarttublex.presenters.PlexBrowsePresenter
import io.reactivex.Observable

/**
 * Injects the Plex [BrowseSection] (+ row observable when ready) into upstream [BrowsePresenter].
 *
 * Upstream has no Plex hooks; private maps are filled via reflection.
 */
object PlexBrowseInstaller {
    @Volatile
    private var lastInjectedPresenter: BrowsePresenter? = null

    fun ensureInstalled(context: Context) {
        install(context, forceRefresh = false)
    }

    /** Re-inject section (e.g. after sign-in / server pick / Retry) and refresh the browse UI. */
    fun refresh(context: Context) {
        PlexBrowseErrorHandler.clearSticky()
        install(context, forceRefresh = true)
    }

    private fun install(context: Context, forceRefresh: Boolean) {
        val appContext = context.applicationContext
        val presenter = try {
            BrowsePresenter.instance(appContext)
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "PlexBrowseInstaller: BrowsePresenter not ready", t)
            return
        }

        val ready = SidebarSectionRegistry.isPlexReady(appContext)
        val section = SidebarSectionRegistry.createPlexSection(appContext)
        if (!injectSectionMapping(presenter, section)) {
            return
        }
        // Only attach row loading when the section is actually a row browser (not sticky error).
        val loadRows = ready && section.type == BrowseSection.TYPE_ROW
        if (!injectRowMapping(presenter, appContext, loadRows)) {
            return
        }

        val sidebar = SidebarService.instance(appContext)
        val wasPinned = sidebar.isSectionPinned(SidebarSectionRegistry.TYPE_PLEX)
        if (!wasPinned) {
            Log.i(SmartTublexApplication.TAG, "PlexBrowseInstaller: enabling TYPE_PLEX section")
            // enableSection uses default-section index (wrong for TYPE_PLEX) and calls updateSections()
            presenter.enableSection(SidebarSectionRegistry.TYPE_PLEX, true)
        }

        // Keep Plex directly under Startseite (TYPE_HOME), matching SmartTube fork behavior.
        val moved = placePlexAfterHome(sidebar)
        if (moved || (wasPinned && (forceRefresh || lastInjectedPresenter !== presenter))) {
            presenter.updateSections()
        }
        lastInjectedPresenter = presenter
        Log.i(
            SmartTublexApplication.TAG,
            "PlexBrowseInstaller: Plex sidebar ready " +
                "(pinned=${sidebar.isSectionPinned(SidebarSectionRegistry.TYPE_PLEX)}, " +
                "ready=$ready, type=${section.type}, afterHome=$moved)"
        )
    }

    /**
     * Moves the pinned Plex section to the slot right after Home.
     * @return true if the pin order changed
     */
    @Suppress("UNCHECKED_CAST")
    private fun placePlexAfterHome(sidebar: SidebarService): Boolean {
        return try {
            val field = SidebarService::class.java.getDeclaredField("mPinnedItems")
            field.isAccessible = true
            val items = field.get(sidebar) as MutableList<Video>

            val homeIndex = items.indexOfFirst { it != null && it.sectionId == MediaGroup.TYPE_HOME }
            val plexIndex = items.indexOfFirst {
                it != null && it.sectionId == SidebarSectionRegistry.TYPE_PLEX
            }
            if (plexIndex < 0) {
                return false
            }
            if (homeIndex >= 0 && plexIndex == homeIndex + 1) {
                return false
            }

            val plex = items.removeAt(plexIndex)
            val insertAt = when {
                homeIndex < 0 -> 0
                // Removing an item before Home shifts Home left by one.
                plexIndex < homeIndex -> homeIndex
                else -> homeIndex + 1
            }.coerceIn(0, items.size)
            items.add(insertAt, plex)
            sidebar.persistState()
            Log.i(
                SmartTublexApplication.TAG,
                "PlexBrowseInstaller: moved TYPE_PLEX after Home (index $insertAt)"
            )
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "PlexBrowseInstaller: failed to place TYPE_PLEX after Home",
                t
            )
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectSectionMapping(presenter: BrowsePresenter, section: BrowseSection): Boolean {
        return try {
            val field = BrowsePresenter::class.java.getDeclaredField("mSectionsMapping")
            field.isAccessible = true
            val mapping = field.get(presenter) as MutableMap<Int, BrowseSection>
            mapping[SidebarSectionRegistry.TYPE_PLEX] = section
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "PlexBrowseInstaller: failed to inject mSectionsMapping",
                t
            )
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectRowMapping(
        presenter: BrowsePresenter,
        context: Context,
        ready: Boolean
    ): Boolean {
        return try {
            val field = BrowsePresenter::class.java.getDeclaredField("mRowMapping")
            field.isAccessible = true
            val mapping = field.get(presenter) as MutableMap<Int, Observable<List<MediaGroup>>>
            if (ready) {
                mapping[SidebarSectionRegistry.TYPE_PLEX] = PlexBrowseErrorHandler.wrapRows(
                    context,
                    PlexBrowsePresenter.getLibraryRowsObserve()
                )
                Log.i(SmartTublexApplication.TAG, "PlexBrowseInstaller: mRowMapping[TYPE_PLEX] set")
            } else {
                mapping.remove(SidebarSectionRegistry.TYPE_PLEX)
            }
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "PlexBrowseInstaller: failed to inject mRowMapping",
                t
            )
            false
        }
    }
}
