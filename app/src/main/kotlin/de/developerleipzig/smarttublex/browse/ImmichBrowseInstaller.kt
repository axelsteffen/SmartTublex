package de.developerleipzig.smarttublex.browse

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import de.developerleipzig.immichapi.ImmichServiceManager
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry

/**
 * Injects the Immich [BrowseSection] into upstream [BrowsePresenter].
 *
 * Row observables (albums / recent videos) arrive in Phase 3d.
 */
object ImmichBrowseInstaller {
    @Volatile
    private var lastInjectedPresenter: BrowsePresenter? = null

    fun ensureInstalled(context: Context) {
        install(context, forceRefresh = false)
    }

    /** Re-inject section after sign-in / sign-out. */
    fun refresh(context: Context) {
        install(context, forceRefresh = true)
    }

    private fun install(context: Context, forceRefresh: Boolean) {
        val appContext = context.applicationContext
        ImmichServiceManager.init(appContext)

        val presenter = try {
            BrowsePresenter.instance(appContext)
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ImmichBrowseInstaller: BrowsePresenter not ready", t)
            return
        }

        val ready = SidebarSectionRegistry.isImmichReady(appContext)
        val section = SidebarSectionRegistry.createImmichSection(appContext)
        if (!injectSectionMapping(presenter, section)) {
            return
        }

        val sidebar = SidebarServiceBridge.instance(appContext)
        val wasPinned = SidebarServiceBridge.isSectionPinned(sidebar, SidebarSectionRegistry.TYPE_IMMICH)
        if (!wasPinned) {
            Log.i(SmartTublexApplication.TAG, "ImmichBrowseInstaller: enabling TYPE_IMMICH section")
            presenter.enableSection(SidebarSectionRegistry.TYPE_IMMICH, true)
        }

        val moved = placeImmichAfterPlex(sidebar)
        if (moved || (wasPinned && (forceRefresh || lastInjectedPresenter !== presenter))) {
            presenter.updateSections()
        }
        lastInjectedPresenter = presenter
        Log.i(
            SmartTublexApplication.TAG,
            "ImmichBrowseInstaller: Immich sidebar ready " +
                "(pinned=${SidebarServiceBridge.isSectionPinned(sidebar, SidebarSectionRegistry.TYPE_IMMICH)}, " +
                "ready=$ready, type=${section.type}, afterPlex=$moved)"
        )
    }

    /** Places Immich under Plex when present, otherwise under Home. */
    private fun placeImmichAfterPlex(sidebar: Any): Boolean {
        return try {
            val items = SidebarServiceBridge.pinnedItems(sidebar)
            val immichIndex = items.indexOfFirst {
                it != null && it.sectionId == SidebarSectionRegistry.TYPE_IMMICH
            }
            if (immichIndex < 0) {
                return false
            }

            val plexIndex = items.indexOfFirst {
                it != null && it.sectionId == SidebarSectionRegistry.TYPE_PLEX
            }
            val homeIndex = items.indexOfFirst {
                it != null && it.sectionId == MediaGroup.TYPE_HOME
            }
            val anchorIndex = when {
                plexIndex >= 0 -> plexIndex
                homeIndex >= 0 -> homeIndex
                else -> -1
            }
            if (anchorIndex >= 0 && immichIndex == anchorIndex + 1) {
                return false
            }

            val immich = items.removeAt(immichIndex)
            val insertAt = when {
                anchorIndex < 0 -> items.size
                immichIndex < anchorIndex -> anchorIndex
                else -> anchorIndex + 1
            }.coerceIn(0, items.size)
            items.add(insertAt, immich)
            SidebarServiceBridge.persistState(sidebar)
            Log.i(
                SmartTublexApplication.TAG,
                "ImmichBrowseInstaller: moved TYPE_IMMICH after anchor (index $insertAt)"
            )
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "ImmichBrowseInstaller: failed to place TYPE_IMMICH",
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
            mapping[SidebarSectionRegistry.TYPE_IMMICH] = section
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "ImmichBrowseInstaller: failed to inject mSectionsMapping",
                t
            )
            false
        }
    }
}
