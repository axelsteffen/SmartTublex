package de.developerleipzig.smarttublex

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter
import de.developerleipzig.plexapi.adapter.PlexMediaItemAdapter
import de.developerleipzig.smarttublex.presenters.PlexBrowsePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

/**
 * Dedicated Plex search screen (Filme + TV-Shows). Deliberately NOT a `SearchPresenter`
 * subclass/override — that upstream presenter has a private constructor and cannot be
 * subclassed, and its View/Fragment wiring is hardcoded internally (no safe injection
 * point without smali patching, which the fork keeps off-limits). Instead this is a plain,
 * wrapper-owned [FragmentActivity] (same pattern as [ImmichImageViewerActivity]), reusing
 * upstream's [RowsSupportFragment] + [VideoCardPresenter] for visual consistency and
 * [VideoActionPresenter] for click routing (movie → play, show → seasons grid via the
 * already-installed [de.developerleipzig.smarttublex.presenters.PlexChannelUploadsPresenter]).
 */
class PlexSearchActivity : FragmentActivity() {
    private var editText: EditText? = null
    private var movieRowAdapter: ArrayObjectAdapter? = null
    private var showRowAdapter: ArrayObjectAdapter? = null
    private var movieGroup: MediaGroup? = null
    private var showGroup: MediaGroup? = null
    private var searchAction: Disposable? = null
    private var continueAction: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val initialQuery = intent.getStringExtra(EXTRA_QUERY)
        if (!initialQuery.isNullOrBlank()) {
            editText?.setText(initialQuery)
            runSearch(initialQuery)
        }
    }

    override fun onDestroy() {
        RxHelper.disposeActions(searchAction, continueAction)
        super.onDestroy()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val input = EditText(this).apply {
            hint = "Filme, TV-Shows suchen …"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    runSearch(text?.toString().orEmpty())
                    true
                } else {
                    false
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(48), dp(32), dp(48), dp(16)) }
        }
        editText = input

        val fragmentContainerId = View.generateViewId()
        val fragmentContainer = FrameLayout(this).apply {
            id = fragmentContainerId
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(input)
        root.addView(fragmentContainer)
        setContentView(root)

        val rowsFragment = RowsSupportFragment()
        supportFragmentManager.beginTransaction()
            .replace(fragmentContainerId, rowsFragment)
            .commitNow()

        val itemPresenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(Video::class.java, VideoCardPresenter())
            addClassPresenter(LoadMoreItem::class.java, LoadMorePresenter())
        }
        movieRowAdapter = ArrayObjectAdapter(itemPresenterSelector)
        showRowAdapter = ArrayObjectAdapter(itemPresenterSelector)

        val rowPresenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }
        val rowsAdapter = ArrayObjectAdapter(rowPresenterSelector).apply {
            add(ListRow(HeaderItem(0, TITLE_MOVIES), movieRowAdapter))
            add(ListRow(HeaderItem(1, TITLE_SHOWS), showRowAdapter))
        }
        rowsFragment.adapter = rowsAdapter

        rowsFragment.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Video -> VideoActionPresenter.instance(this).apply(item)
                is LoadMoreItem -> continueRow(item)
            }
        }

        when (intent.getStringExtra(EXTRA_FOCUS_TYPE)) {
            PlexMediaItemAdapter.SEARCH_ENTRY_SHOW -> rowsFragment.setSelectedPosition(1, false)
            else -> rowsFragment.setSelectedPosition(0, false)
        }
    }

    private fun runSearch(query: String) {
        val trimmed = query.trim()
        RxHelper.disposeActions(searchAction, continueAction)
        movieRowAdapter?.clear()
        showRowAdapter?.clear()
        movieGroup = null
        showGroup = null
        if (trimmed.isEmpty()) return

        val movieAction = PlexBrowsePresenter.getMovieSearchRowObserve(trimmed)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { group -> applyGroup(isMovie = true, group) },
                { error -> Log.e(SmartTublexApplication.TAG, "PlexSearchActivity: movie search failed", error) }
            )
        val showAction = PlexBrowsePresenter.getShowSearchRowObserve(trimmed)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { group -> applyGroup(isMovie = false, group) },
                { error -> Log.e(SmartTublexApplication.TAG, "PlexSearchActivity: show search failed", error) }
            )
        searchAction = CompositeDisposable(movieAction, showAction)
    }

    private fun applyGroup(isMovie: Boolean, group: MediaGroup) {
        val adapter = if (isMovie) movieRowAdapter else showRowAdapter
        if (adapter == null) return
        if (isMovie) movieGroup = group else showGroup = group

        adapter.clear()
        val videoGroup = VideoGroup.from(group)
        videoGroup?.videos?.forEach { adapter.add(it) }
        if (!group.nextPageKey.isNullOrEmpty()) {
            adapter.add(LoadMoreItem(isMovie))
        }
    }

    private fun continueRow(item: LoadMoreItem) {
        val group = if (item.isMovieRow) movieGroup else showGroup
        val adapter = if (item.isMovieRow) movieRowAdapter else showRowAdapter
        if (group == null || adapter == null) return
        val continuation = PlexBrowsePresenter.continueGroupObserve(group) ?: return

        adapter.remove(item)
        RxHelper.disposeActions(continueAction)
        continueAction = continuation
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { continued ->
                    if (item.isMovieRow) movieGroup = continued else showGroup = continued
                    val more = VideoGroup.from(continued)
                    more?.videos?.forEach { adapter.add(it) }
                    if (!continued.nextPageKey.isNullOrEmpty()) {
                        adapter.add(LoadMoreItem(item.isMovieRow))
                    }
                },
                { error -> Log.e(SmartTublexApplication.TAG, "PlexSearchActivity: continuation failed", error) }
            )
    }

    /** Trailing row item shown once a search shelf has a next page (single-library setups only). */
    private class LoadMoreItem(val isMovieRow: Boolean)

    private class LoadMorePresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val density = parent.resources.displayMetrics.density
            val view = TextView(parent.context).apply {
                text = "Mehr laden"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.DKGRAY)
                isFocusable = true
                isFocusableInTouchMode = true
                layoutParams = ViewGroup.LayoutParams((160 * density).toInt(), (120 * density).toInt())
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            // Static label; nothing to bind.
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            // Nothing to release.
        }
    }

    companion object {
        private const val TITLE_MOVIES = "Filme"
        private const val TITLE_SHOWS = "TV-Shows"
        private const val EXTRA_QUERY = "plex_search_query"
        private const val EXTRA_FOCUS_TYPE = "plex_search_focus_type"
        private const val ACTION_SEARCH_PLEX = "de.developerleipzig.smarttublex.action.SEARCH_PLEX"

        /**
         * @param focusType optional [PlexMediaItemAdapter.SEARCH_ENTRY_MOVIE]/`SEARCH_ENTRY_SHOW`
         *   marker — only used to pick the initially focused row; both rows always load.
         * @return true when [startActivity] was invoked without throwing
         */
        fun open(context: Context, focusType: String? = null): Boolean {
            val intent = Intent(ACTION_SEARCH_PLEX).apply {
                component = ComponentName(context.packageName, PlexSearchActivity::class.java.name)
                if (!focusType.isNullOrEmpty()) putExtra(EXTRA_FOCUS_TYPE, focusType)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                Log.i(SmartTublexApplication.TAG, "PlexSearchActivity: startActivity ok focusType=$focusType")
                true
            } catch (e: ActivityNotFoundException) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "PlexSearchActivity: activity not in manifest — rebuild packageWrapperApk",
                    e
                )
                false
            } catch (t: Throwable) {
                Log.e(SmartTublexApplication.TAG, "PlexSearchActivity: startActivity failed", t)
                false
            }
        }
    }
}
