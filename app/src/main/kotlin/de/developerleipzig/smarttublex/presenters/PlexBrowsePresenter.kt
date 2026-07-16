package de.developerleipzig.smarttublex.presenters

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.plexapi.adapter.PlexMediaGroupAdapter
import com.liskovsoft.plexapi.library.PlexPage
import com.liskovsoft.plexapi.network.PlexPmsApi
import com.liskovsoft.plexserviceinterfaces.PlexLibraryService
import com.liskovsoft.plexserviceinterfaces.data.PlexHubGroup
import com.liskovsoft.plexserviceinterfaces.data.PlexLibrary
import com.liskovsoft.plexserviceinterfaces.data.PlexMediaItem
import com.liskovsoft.plexserviceinterfaces.data.PlexMediaPage
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.sharedutils.rx.RxHelper
import de.developerleipzig.smarttublex.SmartTublexApplication
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import java.util.Locale

/**
 * Phase 3c: Plex home-style browse rows for upstream [com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter].
 *
 * Port of the SmartTube-fork presenter; strings are hardcoded (app resources are not in the wrapped APK).
 */
object PlexBrowsePresenter {
    private const val TYPE_MOVIE = "movie"
    private const val TYPE_SHOW = "show"
    private const val MERGE_PAGE_CAP = 50

    private const val ROW_CONTINUE_MOVIES = "Continue Watching"
    private const val ROW_CONTINUE_SHOWS = "Continue Watching (TV)"
    private const val ROW_WATCHLIST = "Watchlist"
    private const val ROW_RECENT_MOVIES = "Recently Added Movies"
    private const val ROW_RECENT_SHOWS = "Recently Added TV"
    private const val ROW_MOVIES = "Movies"
    private const val ROW_SHOWS = "TV Shows"

    fun isPlexGroup(group: MediaGroup?): Boolean = group is PlexMediaGroupAdapter

    /**
     * Cold observable: emits Home-style rows progressively (TV-friendly).
     */
    fun getLibraryRowsObserve(): Observable<List<MediaGroup>> {
        return RxHelper.createLong { emitter ->
            try {
                val libraryService = PlexServiceManager.instance().libraryService
                val libraries = libraryService.librariesObserve.blockingFirst()

                if (libraries.isNullOrEmpty()) {
                    Log.i(SmartTublexApplication.TAG, "PlexBrowsePresenter: no libraries")
                    emitter.onNext(emptyList())
                    emitter.onComplete()
                    return@createLong
                }

                val movieLibraries = ArrayList<PlexLibrary>()
                val showLibraries = ArrayList<PlexLibrary>()
                for (library in libraries) {
                    when {
                        isMovieLibrary(library) -> movieLibraries.add(library)
                        isShowLibrary(library) -> showLibraries.add(library)
                    }
                }

                var emitted = 0
                if (movieLibraries.isNotEmpty()) {
                    emitted += emitMovieRows(emitter, libraryService, movieLibraries)
                }
                if (showLibraries.isNotEmpty()) {
                    emitted += emitShowRows(emitter, libraryService, showLibraries)
                }

                if (emitted == 0 && !emitter.isDisposed) {
                    emitter.onNext(emptyList())
                }
                if (!emitter.isDisposed) {
                    Log.i(SmartTublexApplication.TAG, "PlexBrowsePresenter: finished rows emitted=$emitted")
                    emitter.onComplete()
                }
            } catch (e: Throwable) {
                if (!emitter.isDisposed) {
                    emitter.onError(e)
                }
            }
        }
    }

    /**
     * Next page for a Plex row (scroll-end). Drill-down grids need Video.isPlex (Phase 3d+).
     */
    fun continueGroupObserve(group: MediaGroup?): Observable<MediaGroup>? {
        if (group !is PlexMediaGroupAdapter) return null
        return RxHelper.fromCallable { fetchContinueGroup(group) }
    }

    private fun emitMovieRows(
        emitter: ObservableEmitter<List<MediaGroup>>,
        libraryService: PlexLibraryService,
        movieLibraries: List<PlexLibrary>
    ): Int {
        var emitted = 0
        emitted += emitIfPresent(
            emitter,
            buildMergedShelf(libraryService, movieLibraries, PlexMediaGroupAdapter.Kind.ON_DECK, ROW_CONTINUE_MOVIES)
        )
        emitted += emitIfPresent(emitter, buildWatchlist(libraryService, ROW_WATCHLIST))
        emitted += emitIfPresent(
            emitter,
            buildMergedShelf(
                libraryService,
                movieLibraries,
                PlexMediaGroupAdapter.Kind.RECENTLY_ADDED,
                ROW_RECENT_MOVIES
            )
        )
        emitted += emitIfPresent(
            emitter,
            buildRecommendedRow(libraryService, movieLibraries[0], ROW_MOVIES)
        )
        return emitted
    }

    private fun emitShowRows(
        emitter: ObservableEmitter<List<MediaGroup>>,
        libraryService: PlexLibraryService,
        showLibraries: List<PlexLibrary>
    ): Int {
        var emitted = 0
        emitted += emitIfPresent(
            emitter,
            buildMergedShelf(libraryService, showLibraries, PlexMediaGroupAdapter.Kind.ON_DECK, ROW_CONTINUE_SHOWS)
        )
        emitted += emitIfPresent(
            emitter,
            buildMergedShelf(
                libraryService,
                showLibraries,
                PlexMediaGroupAdapter.Kind.RECENTLY_ADDED,
                ROW_RECENT_SHOWS
            )
        )
        emitted += emitIfPresent(
            emitter,
            buildRecommendedRow(libraryService, showLibraries[0], ROW_SHOWS)
        )
        return emitted
    }

    private fun emitIfPresent(
        emitter: ObservableEmitter<List<MediaGroup>>,
        group: MediaGroup?
    ): Int {
        if (emitter.isDisposed || group == null || group.isEmpty) return 0
        emitter.onNext(listOf(group))
        return 1
    }

    private fun buildMergedShelf(
        libraryService: PlexLibraryService,
        libraries: List<PlexLibrary>,
        kind: PlexMediaGroupAdapter.Kind,
        title: String
    ): MediaGroup? {
        return try {
            val merged = ArrayList<PlexMediaItem>()
            val seen = HashSet<String>()
            var paginationLibrary: PlexLibrary? = null
            var paginationPage: PlexPage? = null

            for (library in libraries) {
                if (merged.size >= MERGE_PAGE_CAP) break
                val page = if (kind == PlexMediaGroupAdapter.Kind.ON_DECK) {
                    toPlexPage(libraryService.getOnDeckPageObserve(library, 0).blockingFirst())
                } else {
                    toPlexPage(libraryService.getRecentlyAddedPageObserve(library, 0).blockingFirst())
                }
                if (page == null || page.items.isEmpty()) continue
                if (paginationLibrary == null) {
                    paginationLibrary = library
                    paginationPage = page
                }
                for (item in page.items) {
                    val key = item.ratingKey ?: continue
                    if (!seen.add(key)) continue
                    merged.add(item)
                    if (merged.size >= MERGE_PAGE_CAP) break
                }
            }

            if (merged.isEmpty()) return null

            val pageForKey = if (libraries.size == 1) paginationPage else null
            val libForKey = if (libraries.size == 1) paginationLibrary else null
            PlexMediaGroupAdapter.fromSimple(title, kind, libForKey, merged, pageForKey)
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: shelf $kind failed", e)
            null
        }
    }

    private fun buildWatchlist(libraryService: PlexLibraryService, title: String): MediaGroup? {
        return try {
            val page = toPlexPage(
                libraryService.getWatchlistPageObserve(PlexPmsApi.TYPE_MOVIE, 0).blockingFirst()
            )
            if (page == null || page.items.isEmpty()) return null
            PlexMediaGroupAdapter.fromSimple(
                title,
                PlexMediaGroupAdapter.Kind.WATCHLIST,
                null,
                PlexPmsApi.TYPE_MOVIE,
                page.items,
                page
            )
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: watchlist failed", e)
            null
        }
    }

    private fun buildRecommendedRow(
        libraryService: PlexLibraryService,
        library: PlexLibrary,
        rowTitle: String
    ): MediaGroup? {
        return try {
            val recommended = collectRecommendedItems(libraryService, library)
            PlexMediaGroupAdapter.fromRecommended(library, rowTitle, recommended, null)
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: recommended ${library.title} failed", e)
            try {
                PlexMediaGroupAdapter.fromRecommended(library, rowTitle, emptyList(), null)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun collectRecommendedItems(
        libraryService: PlexLibraryService,
        library: PlexLibrary
    ): List<PlexMediaItem> {
        val recommended = ArrayList<PlexMediaItem>()
        val seen = HashSet<String>()
        try {
            val hubs = libraryService.getSectionHubsObserve(library).blockingFirst() ?: return recommended
            for (hub in hubs) {
                if (!isRecommendationHub(hub)) continue
                for (item in hub.items) {
                    val key = item.ratingKey ?: continue
                    if (!seen.add(key)) continue
                    recommended.add(item)
                    if (recommended.size >= MERGE_PAGE_CAP) return recommended
                }
            }
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: hubs ${library.title} failed", e)
        }
        return recommended
    }

    internal fun isRecommendationHub(hub: PlexHubGroup?): Boolean {
        if (hub == null) return false
        val id = hub.hubIdentifier?.lowercase(Locale.US).orEmpty()
        val title = hub.title?.lowercase(Locale.US).orEmpty()

        if (id.contains("continue") || id.contains("ondeck") || id.contains("on.deck")
            || id.contains("recentlyadded") || id.contains("recently.added")
            || id.contains("recentlyreleased") || id.contains("inprogress")
        ) {
            return false
        }
        if (title.contains("continue") || title.contains("on deck")
            || title.contains("recently added") || title.contains("in progress")
        ) {
            return false
        }

        return id.contains("recommend") || id.contains("promoted")
            || id.contains("discover") || id.contains("home.movies")
            || id.contains("home.tv") || id.contains("home.video")
            || title.contains("recommend") || title.contains("promoted")
            || title.contains("suggested") || title.contains("for you")
            || title.contains("empfohlen")
    }

    private fun fetchContinueGroup(group: PlexMediaGroupAdapter): MediaGroup? {
        val nextPageKey = group.nextPageKey
        if (nextPageKey.isNullOrEmpty()) return null
        val offset = parseOffset(nextPageKey)
        if (offset < 0) return null

        val libraryService = PlexServiceManager.instance().libraryService
        val page: PlexPage? = when {
            group.isWatchlistGroup -> toPlexPage(
                libraryService.getWatchlistPageObserve(group.watchlistType, offset).blockingFirst()
            )
            group.isOnDeckGroup && group.plexLibrary != null -> toPlexPage(
                libraryService.getOnDeckPageObserve(group.plexLibrary, offset).blockingFirst()
            )
            group.isRecentlyAddedGroup && group.plexLibrary != null -> toPlexPage(
                libraryService.getRecentlyAddedPageObserve(group.plexLibrary, offset).blockingFirst()
            )
            (group.kind == PlexMediaGroupAdapter.Kind.LIBRARY
                || group.kind == PlexMediaGroupAdapter.Kind.LIBRARY_GRID)
                && group.plexLibrary != null -> fetchLibraryPage(libraryService, group.plexLibrary, offset)
            group.isContainerGroup && group.plexContainer != null -> toPlexPage(
                libraryService.getChildrenPageObserve(group.plexContainer, offset).blockingFirst()
            )
            else -> null
        }

        if (page == null || page.items.isEmpty()) return null
        return PlexMediaGroupAdapter.continueFrom(group, page.items, page)
    }

    private fun fetchLibraryPage(
        libraryService: PlexLibraryService,
        library: PlexLibrary,
        offset: Int
    ): PlexPage? {
        val page: PlexMediaPage = when {
            isMovieLibrary(library) ->
                libraryService.getMoviesPageObserve(library, offset).blockingFirst()
            isShowLibrary(library) ->
                libraryService.getShowsPageObserve(library, offset).blockingFirst()
            else -> return null
        }
        return toPlexPage(page)
    }

    private fun toPlexPage(page: PlexMediaPage?): PlexPage? {
        if (page == null) return null
        return PlexPage(page.items, page.offset, page.totalSize)
    }

    private fun parseOffset(nextPageKey: String): Int {
        return try {
            nextPageKey.toInt()
        } catch (_: NumberFormatException) {
            Log.e(SmartTublexApplication.TAG, "Invalid Plex nextPageKey: $nextPageKey")
            -1
        }
    }

    private fun isMovieLibrary(library: PlexLibrary?): Boolean =
        library?.type.equals(TYPE_MOVIE, ignoreCase = true)

    private fun isShowLibrary(library: PlexLibrary?): Boolean =
        library?.type.equals(TYPE_SHOW, ignoreCase = true)

    @Suppress("unused")
    private fun resolveContext(): Context? {
        return try {
            GlobalPreferences.context()
        } catch (_: Throwable) {
            null
        }
    }
}
