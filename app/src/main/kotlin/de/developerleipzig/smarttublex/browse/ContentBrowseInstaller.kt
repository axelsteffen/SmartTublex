package de.developerleipzig.smarttublex.browse

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import de.developerleipzig.immichapi.ImmichServiceManager
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.errors.PlexBrowseErrorHandler
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import de.developerleipzig.smarttublex.presenters.ImmichBrowsePresenter
import de.developerleipzig.smarttublex.presenters.PlexBrowsePresenter
import io.reactivex.Observable

/**
 * Injects the five content [BrowseSection]s into upstream [BrowsePresenter]
 * (Filme, TV-Shows, Merkliste, Fotos, Alben).
 */
object ContentBrowseInstaller {
    @Volatile
    private var lastInjectedPresenter: BrowsePresenter? = null

    fun ensureInstalled(context: Context) {
        install(context, forceRefresh = false)
    }

    fun refresh(context: Context) {
        PlexBrowseErrorHandler.clearSticky()
        install(context, forceRefresh = true)
    }

    private fun install(context: Context, forceRefresh: Boolean) {
        val appContext = context.applicationContext
        ImmichServiceManager.init(appContext)

        val presenter = try {
            BrowsePresenter.instance(appContext)
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ContentBrowseInstaller: BrowsePresenter not ready", t)
            return
        }

        val plexReady = SidebarSectionRegistry.isPlexReady(appContext)
        val immichReady = SidebarSectionRegistry.isImmichReady(appContext)

        val sections = listOf(
            SidebarSectionRegistry.createMoviesSection(appContext),
            SidebarSectionRegistry.createShowsSection(appContext),
            SidebarSectionRegistry.createWatchlistSection(appContext),
            SidebarSectionRegistry.createPhotosSection(appContext),
            SidebarSectionRegistry.createAlbumsSection(appContext)
        )
        if (!injectSectionMapping(presenter, sections)) {
            return
        }
        if (!injectRowMapping(presenter, appContext, plexReady, immichReady, sections)) {
            return
        }

        val sidebar = SidebarServiceBridge.instance(appContext)
        var anyEnabled = false
        for (id in SidebarSectionRegistry.CONTENT_SECTION_IDS) {
            if (!SidebarServiceBridge.isSectionPinned(sidebar, id)) {
                presenter.enableSection(id, true)
                anyEnabled = true
            }
        }

        val moved = placeContentSectionsAfterHome(sidebar)
        if (moved || anyEnabled || forceRefresh || lastInjectedPresenter !== presenter) {
            presenter.updateSections()
        }
        lastInjectedPresenter = presenter
        Log.i(
            SmartTublexApplication.TAG,
            "ContentBrowseInstaller: content sidebar ready " +
                "(plexReady=$plexReady, immichReady=$immichReady, moved=$moved)"
        )
    }

    /**
     * Pins Filme → TV-Shows → Merkliste → Fotos → Alben immediately after Home.
     * @return true only when the pin order actually changed (avoids Browse reload flicker).
     */
    private fun placeContentSectionsAfterHome(sidebar: Any): Boolean {
        return try {
            val items = SidebarServiceBridge.pinnedItems(sidebar)
            val homeIndex = items.indexOfFirst { it != null && it.sectionId == MediaGroup.TYPE_HOME }
            val insertBase = if (homeIndex < 0) 0 else homeIndex + 1
            val expectedIds = SidebarSectionRegistry.CONTENT_SECTION_IDS
            val alreadyOrdered =
                insertBase + expectedIds.size <= items.size &&
                    expectedIds.indices.all { i ->
                        items[insertBase + i]?.sectionId == expectedIds[i]
                    }
            if (alreadyOrdered) {
                return false
            }

            val contentItems = ArrayList<com.liskovsoft.smartyoutubetv2.common.app.models.data.Video>()
            for (id in expectedIds) {
                val idx = items.indexOfFirst { it != null && it.sectionId == id }
                if (idx >= 0) {
                    contentItems.add(items.removeAt(idx))
                }
            }
            if (contentItems.isEmpty()) {
                return false
            }

            val homeIndexAfter = items.indexOfFirst { it != null && it.sectionId == MediaGroup.TYPE_HOME }
            val insertAtBase = if (homeIndexAfter < 0) 0 else homeIndexAfter + 1
            var insertAt = insertAtBase.coerceIn(0, items.size)
            for (item in contentItems) {
                items.add(insertAt++, item)
            }
            SidebarServiceBridge.persistState(sidebar)
            Log.i(
                SmartTublexApplication.TAG,
                "ContentBrowseInstaller: placed content sections after Home (base=$insertAtBase)"
            )
            true
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "ContentBrowseInstaller: pin order failed", t)
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectSectionMapping(
        presenter: BrowsePresenter,
        sections: List<BrowseSection>
    ): Boolean {
        return try {
            val field = BrowsePresenter::class.java.getDeclaredField("mSectionsMapping")
            field.isAccessible = true
            val mapping = field.get(presenter) as MutableMap<Int, BrowseSection>
            for (section in sections) {
                mapping[section.id] = section
            }
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "ContentBrowseInstaller: failed to inject mSectionsMapping",
                t
            )
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectRowMapping(
        presenter: BrowsePresenter,
        context: Context,
        plexReady: Boolean,
        immichReady: Boolean,
        sections: List<BrowseSection>
    ): Boolean {
        return try {
            val field = BrowsePresenter::class.java.getDeclaredField("mRowMapping")
            field.isAccessible = true
            val mapping = field.get(presenter) as MutableMap<Int, Observable<List<MediaGroup>>>

            fun attach(id: Int, ready: Boolean, rows: () -> Observable<List<MediaGroup>>) {
                val section = sections.firstOrNull { it.id == id }
                val load = ready && section?.type == BrowseSection.TYPE_ROW
                if (load) {
                    mapping[id] = if (SidebarSectionRegistry.isPlexSection(id)) {
                        PlexBrowseErrorHandler.wrapRows(context, rows())
                    } else {
                        rows()
                    }
                } else {
                    mapping.remove(id)
                }
            }

            attach(SidebarSectionRegistry.TYPE_MOVIES, plexReady) {
                PlexBrowsePresenter.getMoviesRowsObserve()
            }
            attach(SidebarSectionRegistry.TYPE_SHOWS, plexReady) {
                PlexBrowsePresenter.getShowsRowsObserve()
            }
            attach(SidebarSectionRegistry.TYPE_WATCHLIST, plexReady) {
                PlexBrowsePresenter.getWatchlistRowsObserve()
            }
            attach(SidebarSectionRegistry.TYPE_PHOTOS, immichReady) {
                ImmichBrowsePresenter.getPhotosRowsObserve()
            }
            attach(SidebarSectionRegistry.TYPE_ALBUMS, immichReady) {
                ImmichBrowsePresenter.getAlbumsRowsObserve()
            }
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "ContentBrowseInstaller: failed to inject mRowMapping",
                t
            )
            false
        }
    }
}
