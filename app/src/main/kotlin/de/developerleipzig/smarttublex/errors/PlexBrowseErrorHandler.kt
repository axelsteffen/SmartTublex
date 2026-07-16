package de.developerleipzig.smarttublex.errors

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView
import com.liskovsoft.smartyoutubetv2.common.R
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import io.reactivex.Observable

/**
 * Surfaces Plex browse failures as a clear error fragment instead of the stock
 * [com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError] stack dump.
 *
 * Strategy: switch the Plex sidebar section to [BrowseSection.TYPE_ERROR], and also
 * call [BrowseView.showError] after the upstream error handler so our message wins.
 */
object PlexBrowseErrorHandler {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Wraps the library-rows observable so connectivity failures become a user-facing error.
     */
    fun wrapRows(context: Context, source: Observable<List<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup>>):
        Observable<List<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup>> {
        val appContext = context.applicationContext
        return source.onErrorResumeNext { error: Throwable ->
            Log.e(SmartTublexApplication.TAG, "PlexBrowseErrorHandler: browse failed", error)
            // Run after upstream handleLoadError(null) → CategoryEmptyError so our message wins.
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
        val placeholder = PlexSignInPlaceholder(appContext, mode)

        try {
            val presenter = BrowsePresenter.instance(appContext)
            injectErrorSection(presenter, placeholder)
            val view = presenter.view as? BrowseView
            view?.showProgressBar(false)
            // Upstream handleLoadError may show CategoryEmptyError first; overwrite it.
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
    private fun injectErrorSection(
        presenter: BrowsePresenter,
        placeholder: PlexSignInPlaceholder
    ) {
        val section = BrowseSection(
            SidebarSectionRegistry.TYPE_PLEX,
            SidebarSectionRegistry.TITLE_PLEX,
            BrowseSection.TYPE_ERROR,
            R.drawable.icon_playlist,
            false,
            placeholder
        )
        try {
            val sectionsField = BrowsePresenter::class.java.getDeclaredField("mSectionsMapping")
            sectionsField.isAccessible = true
            val sections = sectionsField.get(presenter) as MutableMap<Int, BrowseSection>
            sections[SidebarSectionRegistry.TYPE_PLEX] = section

            val rowsField = BrowsePresenter::class.java.getDeclaredField("mRowMapping")
            rowsField.isAccessible = true
            val rows = rowsField.get(presenter) as MutableMap<*, *>
            rows.remove(SidebarSectionRegistry.TYPE_PLEX)
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowseErrorHandler: inject failed", t)
        }
    }
}
