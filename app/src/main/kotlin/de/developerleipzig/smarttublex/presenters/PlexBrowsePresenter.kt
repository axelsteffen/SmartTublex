package de.developerleipzig.smarttublex.presenters

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import de.developerleipzig.plexapi.PlexServiceManager
import de.developerleipzig.plexapi.adapter.PlexMediaGroupAdapter
import de.developerleipzig.plexapi.library.PlexLibraryImpl
import de.developerleipzig.plexapi.library.PlexPage
import de.developerleipzig.plexapi.network.PlexPmsApi
import de.developerleipzig.plexserviceinterfaces.PlexLibraryService
import de.developerleipzig.plexserviceinterfaces.data.PlexLibrary
import de.developerleipzig.plexserviceinterfaces.data.PlexMediaItem
import de.developerleipzig.plexserviceinterfaces.data.PlexMediaPage
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.PlexPlaybackBridge
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import java.util.Locale

/**
 * Plex content browse rows for Filme / TV-Shows / Merkliste sidebar sections.
 */
object PlexBrowsePresenter {
    private const val TYPE_MOVIE = "movie"
    private const val TYPE_SHOW = "show"
    private const val MERGE_PAGE_CAP = 50

    private const val ROW_CONTINUE = "Continue Watching"
    private const val ROW_RECENT_MOVIES = "Recently Added"
    private const val ROW_RECENT_SHOWS = "Recently Added"
    private const val ROW_ALL_MOVIES = "Alle Filme"
    private const val ROW_ALL_SHOWS = "Alle TV-Shows"
    private const val CARD_ALL_MOVIES = "Alle Filme"
    private const val CARD_ALL_SHOWS = "Alle TV-Shows"
    private const val ROW_WATCHLIST = "Merkliste"

    fun isPlexGroup(group: MediaGroup?): Boolean = group is PlexMediaGroupAdapter

    fun getMoviesRowsObserve(): Observable<List<MediaGroup>> {
        return libraryRowsObserve { emitter, libraryService, movieLibraries, _ ->
            emitMovieRows(emitter, libraryService, movieLibraries)
        }
    }

    fun getShowsRowsObserve(): Observable<List<MediaGroup>> {
        return libraryRowsObserve { emitter, libraryService, _, showLibraries ->
            emitShowRows(emitter, libraryService, showLibraries)
        }
    }

    fun getWatchlistRowsObserve(): Observable<List<MediaGroup>> {
        return RxHelper.createLong { emitter ->
            try {
                val libraryService = PlexServiceManager.instance().libraryService
                val result = emitIfPresent(emitter, buildMergedWatchlist(libraryService, ROW_WATCHLIST))
                if (result.emitted == 0 && result.error != null) {
                    if (!emitter.isDisposed) emitter.onError(result.error)
                    return@createLong
                }
                if (result.emitted == 0 && !emitter.isDisposed) {
                    emitter.onNext(emptyList())
                }
                if (!emitter.isDisposed) emitter.onComplete()
            } catch (e: Throwable) {
                if (!emitter.isDisposed) emitter.onError(e)
            }
        }
    }

    /** @deprecated Use section-specific observatories. */
    fun getLibraryRowsObserve(): Observable<List<MediaGroup>> = getMoviesRowsObserve()

    fun continueGroupObserve(group: MediaGroup?): Observable<MediaGroup>? {
        if (group !is PlexMediaGroupAdapter) return null
        return RxHelper.fromCallable { fetchContinueGroup(group) }
    }

    fun getLibraryGridObserve(video: Video?): Observable<MediaGroup>? {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video) || !video.hasReloadPageKey()) {
            return null
        }
        return RxHelper.fromCallable { fetchLibraryGrid(video) }
    }

    fun getChildrenGroupObserve(video: Video?): Observable<MediaGroup>? {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video) || !video.hasPlaylist()) {
            return null
        }
        return RxHelper.fromCallable { fetchChildrenGroup(video) }
    }

    private fun libraryRowsObserve(
        emit: (
            ObservableEmitter<List<MediaGroup>>,
            PlexLibraryService,
            List<PlexLibrary>,
            List<PlexLibrary>
        ) -> EmitResult
    ): Observable<List<MediaGroup>> {
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

                val result = emit(emitter, libraryService, movieLibraries, showLibraries)
                if (result.emitted == 0 && result.error != null) {
                    if (!emitter.isDisposed) emitter.onError(result.error)
                    return@createLong
                }
                if (result.emitted == 0 && !emitter.isDisposed) {
                    emitter.onNext(emptyList())
                }
                if (!emitter.isDisposed) {
                    Log.i(
                        SmartTublexApplication.TAG,
                        "PlexBrowsePresenter: finished rows emitted=${result.emitted}"
                    )
                    emitter.onComplete()
                }
            } catch (e: Throwable) {
                if (!emitter.isDisposed) emitter.onError(e)
            }
        }
    }

    private data class EmitResult(val emitted: Int, val error: Throwable? = null)

    private fun emitMovieRows(
        emitter: ObservableEmitter<List<MediaGroup>>,
        libraryService: PlexLibraryService,
        movieLibraries: List<PlexLibrary>
    ): EmitResult {
        if (movieLibraries.isEmpty()) return EmitResult(0)
        var emitted = 0
        var error: Throwable? = null
        emitIfPresent(
            emitter,
            buildMergedShelf(libraryService, movieLibraries, PlexMediaGroupAdapter.Kind.ON_DECK, ROW_CONTINUE)
        ).also {
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
            buildAllLibrariesCard(movieLibraries, ROW_ALL_MOVIES, CARD_ALL_MOVIES)
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
        if (showLibraries.isEmpty()) return EmitResult(0)
        var emitted = 0
        var error: Throwable? = null
        emitIfPresent(
            emitter,
            buildMergedShelf(libraryService, showLibraries, PlexMediaGroupAdapter.Kind.ON_DECK, ROW_CONTINUE)
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
            buildAllLibrariesCard(showLibraries, ROW_ALL_SHOWS, CARD_ALL_SHOWS)
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

    private fun buildAllLibrariesCard(
        libraries: List<PlexLibrary>,
        rowTitle: String,
        cardTitle: String
    ): BuiltGroup {
        val library = libraries.firstOrNull() ?: return BuiltGroup(null)
        return try {
            BuiltGroup(PlexMediaGroupAdapter.fromBrowseCard(library, rowTitle, cardTitle))
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: browse card failed", e)
            BuiltGroup(null, e)
        }
    }

    /**
     * Merges movie + show watchlist pages (capped), sorts by year descending.
     */
    private fun buildMergedWatchlist(libraryService: PlexLibraryService, title: String): BuiltGroup {
        return try {
            val merged = ArrayList<PlexMediaItem>()
            val seen = HashSet<String>()
            for (type in intArrayOf(PlexPmsApi.TYPE_MOVIE, PlexPmsApi.TYPE_SHOW)) {
                if (merged.size >= MERGE_PAGE_CAP) break
                val page = toPlexPage(libraryService.getWatchlistPageObserve(type, 0).blockingFirst())
                if (page == null || page.items.isEmpty()) continue
                for (item in page.items) {
                    val key = item.ratingKey ?: continue
                    if (!seen.add(key)) continue
                    merged.add(item)
                    if (merged.size >= MERGE_PAGE_CAP) break
                }
            }
            if (merged.isEmpty()) return BuiltGroup(null)
            merged.sortWith(
                compareByDescending<PlexMediaItem> { if (it.year > 0) it.year else Int.MIN_VALUE }
                    .thenBy { it.title?.lowercase(Locale.US).orEmpty() }
            )
            BuiltGroup(
                PlexMediaGroupAdapter.fromSimple(
                    title,
                    PlexMediaGroupAdapter.Kind.WATCHLIST,
                    null,
                    0,
                    merged,
                    null
                )
            )
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexBrowsePresenter: watchlist failed", e)
            BuiltGroup(null, e)
        }
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
                || group.kind == PlexMediaGroupAdapter.Kind.LIBRARY_GRID
                || group.kind == PlexMediaGroupAdapter.Kind.HUB_RECOMMENDED)
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
