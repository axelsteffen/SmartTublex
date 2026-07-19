package de.developerleipzig.smarttublex.errors

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import io.reactivex.Observable

/**
 * Surfaces Plex browse failures as a clear error fragment instead of the stock
 * [com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError] stack dump.
 *
 * Keeps a sticky [BrowseSection.TYPE_ERROR] in the sidebar mapping so leaving and
 * returning to Plex still shows the message (via [BrowseSectionFragmentFactory]).
 */
object PlexBrowseErrorHandler {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Last browse failure mode; cleared on successful retry / refresh. */
    @Volatile
    var stickyMode: PlexSignInPlaceholder.Mode? = null
        private set

    fun clearSticky() {
        stickyMode = null
    }

    /**
     * Wraps the library-rows observable so connectivity failures become a user-facing error.
     */
    fun wrapRows(
        context: Context,
        source: Observable<List<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup>>
    ): Observable<List<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup>> {
        val appContext = context.applicationContext
        return source.onErrorResumeNext { error: Throwable ->
            Log.e(SmartTublexApplication.TAG, "PlexBrowseErrorHandler: browse failed", error)
            // After upstream handleLoadError(null) → CategoryEmptyError so our section wins.
            mainHandler.postDelayed({ present(appContext, error) }, 100)
            Observable.empty()
        }
    }

    fun present(context: Context, error: Throwable?) {
        val appContext = context.applicationContext
        val kind = PlexErrorClassifier.classify(error)
        val mode = when (kind) {
            PlexErrorClassifier.Kind.AUTH -> PlexSignInPlaceholder.Mode.AUTH_EXPIRED
            PlexErrorClassifier.Kind.OFFLINE -> PlexSignInPlaceholder.Mode.SERVER_UNAVAILABLE
            PlexErrorClassifier.Kind.GENERIC -> PlexSignInPlaceholder.Mode.LOAD_FAILED
        }
        stickyMode = mode
        val placeholder = PlexSignInPlaceholder(appContext, mode)

        try {
            val presenter = BrowsePresenter.instance(appContext)
            injectErrorSections(presenter, placeholder)
            // Rebuild sidebar headers from mSectionsMapping so return visits use TYPE_ERROR
            // (ErrorDialogFragment) instead of the stale TYPE_ROW with no row mapping.
            presenter.updateSections()
            val view = presenter.view as? BrowseView
            view?.showProgressBar(false)
            view?.showError(placeholder)
            Log.i(
                SmartTublexApplication.TAG,
                "PlexBrowseErrorHandler: showing $mode (${PlexErrorClassifier.browseMessage(error)})"
            )
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowseErrorHandler: failed to present error", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectErrorSections(
        presenter: BrowsePresenter,
        placeholder: PlexSignInPlaceholder
    ) {
        try {
            val sectionsField = BrowsePresenter::class.java.getDeclaredField("mSectionsMapping")
            sectionsField.isAccessible = true
            val sections = sectionsField.get(presenter) as MutableMap<Int, BrowseSection>
            for (id in intArrayOf(
                SidebarSectionRegistry.TYPE_MOVIES,
                SidebarSectionRegistry.TYPE_SHOWS,
                SidebarSectionRegistry.TYPE_WATCHLIST
            )) {
                val title = when (id) {
                    SidebarSectionRegistry.TYPE_SHOWS -> SidebarSectionRegistry.TITLE_SHOWS
                    SidebarSectionRegistry.TYPE_WATCHLIST -> SidebarSectionRegistry.TITLE_WATCHLIST
                    else -> SidebarSectionRegistry.TITLE_MOVIES
                }
                val icon = when (id) {
                    SidebarSectionRegistry.TYPE_SHOWS -> SidebarSectionRegistry.ICON_SHOWS
                    SidebarSectionRegistry.TYPE_WATCHLIST -> SidebarSectionRegistry.ICON_WATCHLIST
                    else -> SidebarSectionRegistry.ICON_MOVIES
                }
                sections[id] = BrowseSection(
                    id, title, BrowseSection.TYPE_ERROR, icon, false, placeholder
                )
            }

            val rowsField = BrowsePresenter::class.java.getDeclaredField("mRowMapping")
            rowsField.isAccessible = true
            val rows = rowsField.get(presenter) as MutableMap<*, *>
            rows.remove(SidebarSectionRegistry.TYPE_MOVIES)
            rows.remove(SidebarSectionRegistry.TYPE_SHOWS)
            rows.remove(SidebarSectionRegistry.TYPE_WATCHLIST)
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowseErrorHandler: inject failed", t)
        }
    }
}
