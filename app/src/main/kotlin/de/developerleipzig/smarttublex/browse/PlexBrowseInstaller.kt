package de.developerleipzig.smarttublex.browse

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService
import de.developerleipzig.smarttublex.SmartTublexApplication
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

    /** Re-inject section (e.g. after sign-in / server pick) and refresh the browse UI. */
    fun refresh(context: Context) {
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
        if (!injectRowMapping(presenter, ready)) {
            return
        }

        val sidebar = SidebarService.instance(appContext)
        if (!sidebar.isSectionPinned(SidebarSectionRegistry.TYPE_PLEX)) {
            Log.i(SmartTublexApplication.TAG, "PlexBrowseInstaller: enabling TYPE_PLEX section")
            presenter.enableSection(SidebarSectionRegistry.TYPE_PLEX, true)
        } else if (forceRefresh || lastInjectedPresenter !== presenter) {
            presenter.updateSections()
        }
        lastInjectedPresenter = presenter
        Log.i(
            SmartTublexApplication.TAG,
            "PlexBrowseInstaller: Plex sidebar ready " +
                "(pinned=${sidebar.isSectionPinned(SidebarSectionRegistry.TYPE_PLEX)}, " +
                "ready=$ready, type=${section.type})"
        )
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
    private fun injectRowMapping(presenter: BrowsePresenter, ready: Boolean): Boolean {
        return try {
            val field = BrowsePresenter::class.java.getDeclaredField("mRowMapping")
            field.isAccessible = true
            val mapping = field.get(presenter) as MutableMap<Int, Observable<List<MediaGroup>>>
            if (ready) {
                mapping[SidebarSectionRegistry.TYPE_PLEX] = PlexBrowsePresenter.getLibraryRowsObserve()
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
