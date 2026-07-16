package de.developerleipzig.smarttublex.presenters

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.plexapi.adapter.PlexMediaGroupAdapter
import com.liskovsoft.plexapi.library.PlexLibraryImpl
import com.liskovsoft.plexapi.library.PlexPage
import com.liskovsoft.plexapi.network.PlexPmsApi
import com.liskovsoft.plexserviceinterfaces.PlexLibraryService
import com.liskovsoft.plexserviceinterfaces.data.PlexHubGroup
import com.liskovsoft.plexserviceinterfaces.data.PlexLibrary
import com.liskovsoft.plexserviceinterfaces.data.PlexMediaItem
import com.liskovsoft.plexserviceinterfaces.data.PlexMediaPage
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.PlexPlaybackBridge
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
                var softFail: Throwable? = null
                if (movieLibraries.isNotEmpty()) {
                    val result = emitMovieRows(emitter, libraryService, movieLibraries)
                    emitted += result.emitted
                    softFail = softFail ?: result.error
                }
                if (showLibraries.isNotEmpty()) {
                    val result = emitShowRows(emitter, libraryService, showLibraries)
                    emitted += result.emitted
                    softFail = softFail ?: result.error
                }

                if (emitted == 0 && softFail != null) {
                    // All shelves failed — surface as browse error (e.g. PMS went down mid-load).
                    if (!emitter.isDisposed) {
                        emitter.onError(softFail)
                    }
                    return@createLong
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
     * Next page for a Plex row or grid (scroll-end).
     */
    fun continueGroupObserve(group: MediaGroup?): Observable<MediaGroup>? {
        if (group !is PlexMediaGroupAdapter) return null
        return RxHelper.fromCallable { fetchContinueGroup(group) }
    }

    /**
     * Full paginated library grid from a browse stub ([Video.reloadPageKey]).
     */
    fun getLibraryGridObserve(video: Video?): Observable<MediaGroup>? {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video) || !video.hasReloadPageKey()) {
            return null
        }
        return RxHelper.fromCallable { fetchLibraryGrid(video) }
    }

    /**
     * Children of a Plex show or season for [com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter].
     */
    fun getChildrenGroupObserve(video: Video?): Observable<MediaGroup>? {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video) || !video.hasPlaylist()) {
            return null
        }
        return RxHelper.fromCallable { fetchChildrenGroup(video) }
    }

    private data class EmitResult(val emitted: Int, val error: Throwable? = null)

    private fun emitMovieRows(
        emitter: ObservableEmitter<List<MediaGroup>>,
        libraryService: PlexLibraryService,
        movieLibraries: List<PlexLibrary>
    ): EmitResult {
        var emitted = 0
        var error: Throwable? = null
        emitIfPresent(
            emitter,
            buildMergedShelf(libraryService, movieLibraries, PlexMediaGroupAdapter.Kind.ON_DECK, ROW_CONTINUE_MOVIES)
        ).also {
            emitted += it.emitted
            error = error ?: it.error
        }
        emitIfPresent(emitter, buildWatchlist(libraryService, ROW_WATCHLIST)).also {
            emitted += it.emitted
            error = error ?: it.error
        }
        emitIfPresent(
            emitter,
            buildMergedShelf(
                libraryService,
                movieLibraries,
                PlexMediaGroupAdapter.Kind.RECENTLY_ADDED,
                ROW_RECENT_MOVIES
            )
        ).also {
            emitted += it.emitted
            error = error ?: it.error
        }
        emitIfPresent(
            emitter,
            buildRecommendedRow(libraryService, movieLibraries[0], ROW_MOVIES)
        ).also {
            emitted += it.emitted
            error = error ?: it.error
        }
        return EmitResult(emitted, error)
    }

    private fun emitShowRows(
        emitter: ObservableEmitter<List<MediaGroup>>,
        libraryService: PlexLibraryService,
        showLibraries: List<PlexLibrary>
    ): EmitResult {
        var emitted = 0
        var error: Throwable? = null
        emitIfPresent(
            emitter,
            buildMergedShelf(libraryService, showLibraries, PlexMediaGroupAdapter.Kind.ON_DECK, ROW_CONTINUE_SHOWS)
        ).also {
            emitted += it.emitted
            error = error ?: it.error
        }
        emitIfPresent(
            emitter,
            buildMergedShelf(
                libraryService,
                showLibraries,
                PlexMediaGroupAdapter.Kind.RECENTLY_ADDED,
                ROW_RECENT_SHOWS
            )
        ).also {
            emitted += it.emitted
            error = error ?: it.error
        }
        emitIfPresent(
            emitter,
            buildRecommendedRow(libraryService, showLibraries[0], ROW_SHOWS)
        ).also {
            emitted += it.emitted
            error = error ?: it.error
        }
        return EmitResult(emitted, error)
    }

    private fun emitIfPresent(
        emitter: ObservableEmitter<List<MediaGroup>>,
        built: BuiltGroup
    ): EmitResult {
        if (emitter.isDisposed) return EmitResult(0, built.error)
        val group = built.group
        if (group == null || group.isEmpty) return EmitResult(0, built.error)
        emitter.onNext(listOf(group))
        return EmitResult(1, built.error)
    }

    private data class BuiltGroup(val group: MediaGroup?, val error: Throwable? = null)

    private fun buildMergedShelf(
        libraryService: PlexLibraryService,
        libraries: List<PlexLibrary>,
        kind: PlexMediaGroupAdapter.Kind,
        title: String
    ): BuiltGroup {
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

            if (merged.isEmpty()) return BuiltGroup(null)

            val pageForKey = if (libraries.size == 1) paginationPage else null
            val libForKey = if (libraries.size == 1) paginationLibrary else null
            BuiltGroup(PlexMediaGroupAdapter.fromSimple(title, kind, libForKey, merged, pageForKey))
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: shelf $kind failed", e)
            BuiltGroup(null, e)
        }
    }

    private fun buildWatchlist(libraryService: PlexLibraryService, title: String): BuiltGroup {
        return try {
            val page = toPlexPage(
                libraryService.getWatchlistPageObserve(PlexPmsApi.TYPE_MOVIE, 0).blockingFirst()
            )
            if (page == null || page.items.isEmpty()) return BuiltGroup(null)
            BuiltGroup(
                PlexMediaGroupAdapter.fromSimple(
                    title,
                    PlexMediaGroupAdapter.Kind.WATCHLIST,
                    null,
                    PlexPmsApi.TYPE_MOVIE,
                    page.items,
                    page
                )
            )
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: watchlist failed", e)
            BuiltGroup(null, e)
        }
    }

    private fun buildRecommendedRow(
        libraryService: PlexLibraryService,
        library: PlexLibrary,
        rowTitle: String
    ): BuiltGroup {
        return try {
            val recommended = collectRecommendedItems(libraryService, library)
            BuiltGroup(PlexMediaGroupAdapter.fromRecommended(library, rowTitle, recommended, null))
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: recommended ${library.title} failed", e)
            try {
                BuiltGroup(PlexMediaGroupAdapter.fromRecommended(library, rowTitle, emptyList(), null), e)
            } catch (_: Throwable) {
                BuiltGroup(null, e)
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

    private fun fetchLibraryGrid(video: Video): MediaGroup? {
        val libraryKey = video.reloadPageKey ?: return null
        if (libraryKey.isEmpty()) return null

        val libraryType = video.playlistParams ?: TYPE_MOVIE
        val title = video.title ?: libraryKey
        val library = PlexLibraryImpl(libraryKey, title, libraryType)

        val page = fetchLibraryPage(PlexServiceManager.instance().libraryService, library, 0)
        if (page == null || page.items.isEmpty()) return null

        return PlexMediaGroupAdapter.fromLibraryGrid(library, page.items, page)
    }

    private fun fetchChildrenGroup(video: Video): MediaGroup? {
        val parent = PlexPlaybackBridge.resolvePlexItem(video) ?: return null
        val page = toPlexPage(
            PlexServiceManager.instance().libraryService
                .getChildrenPageObserve(parent, 0)
                .blockingFirst()
        )
        if (page == null) return null
        return PlexMediaGroupAdapter.fromContainer(parent, page.items, page)
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
