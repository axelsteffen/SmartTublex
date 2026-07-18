package de.developerleipzig.smarttublex.presenters

import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import de.developerleipzig.immichapi.adapter.ImmichMediaGroupAdapter
import de.developerleipzig.immichapi.adapter.ImmichMediaItemAdapter
import de.developerleipzig.immichapi.library.ImmichAlbumImpl
import de.developerleipzig.immichapi.library.ImmichPage
import de.developerleipzig.immichserviceinterfaces.ImmichLibraryService
import de.developerleipzig.immichserviceinterfaces.data.ImmichAlbum
import de.developerleipzig.immichserviceinterfaces.data.ImmichAssetPage
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.MediaSourceRegistry
import io.reactivex.Observable
import io.reactivex.ObservableEmitter

/**
 * Phase 3d: Immich browse rows (recent videos + albums) for upstream [BrowsePresenter].
 *
 * Progressive emission keeps first paint light on TV; album grids open via ChannelUploads.
 */
object ImmichBrowsePresenter {
    private const val ROW_RECENT_VIDEOS = "Recent Videos"

    fun isImmichGroup(group: MediaGroup?): Boolean = group is ImmichMediaGroupAdapter

    fun isImmichVideo(video: Video?): Boolean {
        if (video == null) return false
        return video.mediaItem is ImmichMediaItemAdapter
    }

    /**
     * Cold observable: emits Immich shelves progressively (TV-friendly).
     */
    fun getLibraryRowsObserve(): Observable<List<MediaGroup>> {
        return RxHelper.createLong { emitter ->
            try {
                val libraryService = MediaSourceRegistry.getImmichServiceManager().libraryService
                var emitted = 0
                var softFail: Throwable? = null

                emitIfPresent(emitter, buildRecentVideos(libraryService)).also {
                    emitted += it.emitted
                    softFail = softFail ?: it.error
                }

                val albums = try {
                    libraryService.albumsObserve.blockingFirst()
                } catch (e: Throwable) {
                    Log.e(SmartTublexApplication.TAG, "ImmichBrowsePresenter: albums failed", e)
                    softFail = softFail ?: e
                    emptyList()
                }

                if (albums.isNullOrEmpty()) {
                    Log.i(SmartTublexApplication.TAG, "ImmichBrowsePresenter: no albums")
                } else {
                    for (album in albums) {
                        if (emitter.isDisposed) break
                        emitIfPresent(emitter, buildAlbumRow(libraryService, album)).also {
                            emitted += it.emitted
                            softFail = softFail ?: it.error
                        }
                    }
                }

                if (emitted == 0 && softFail != null) {
                    if (!emitter.isDisposed) {
                        emitter.onError(softFail)
                    }
                    return@createLong
                }

                if (emitted == 0 && !emitter.isDisposed) {
                    emitter.onNext(emptyList())
                }
                if (!emitter.isDisposed) {
                    Log.i(
                        SmartTublexApplication.TAG,
                        "ImmichBrowsePresenter: finished rows emitted=$emitted"
                    )
                    emitter.onComplete()
                }
            } catch (e: Throwable) {
                if (!emitter.isDisposed) {
                    emitter.onError(e)
                }
            }
        }
    }

    /** Next page for an Immich row or album grid (scroll-end). */
    fun continueGroupObserve(group: MediaGroup?): Observable<MediaGroup>? {
        if (group !is ImmichMediaGroupAdapter) return null
        return RxHelper.fromCallable { fetchContinueGroup(group) }
    }

    /** Full paginated album grid from a browse stub ([Video.reloadPageKey]). */
    fun getAlbumGridObserve(video: Video?): Observable<MediaGroup>? {
        if (video == null || !isImmichVideo(video) || !video.hasReloadPageKey()) {
            return null
        }
        return RxHelper.fromCallable { fetchAlbumGrid(video) }
    }

    private data class EmitResult(val emitted: Int, val error: Throwable? = null)

    private data class BuiltGroup(val group: MediaGroup?, val error: Throwable? = null)

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

    private fun buildRecentVideos(libraryService: ImmichLibraryService): BuiltGroup {
        return try {
            val page = toImmichPage(libraryService.getRecentVideosPageObserve(0).blockingFirst())
            if (page == null || page.items.isEmpty()) return BuiltGroup(null)
            BuiltGroup(ImmichMediaGroupAdapter.fromRecentVideos(ROW_RECENT_VIDEOS, page.items, page))
        } catch (e: Throwable) {
            Log.e(SmartTublexApplication.TAG, "ImmichBrowsePresenter: recent videos failed", e)
            BuiltGroup(null, e)
        }
    }

    private fun buildAlbumRow(libraryService: ImmichLibraryService, album: ImmichAlbum): BuiltGroup {
        return try {
            val page = toImmichPage(
                libraryService.getAlbumAssetsPageObserve(album, 0).blockingFirst()
            )
            BuiltGroup(ImmichMediaGroupAdapter.fromAlbum(album, page?.items, page))
        } catch (e: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "ImmichBrowsePresenter: album row ${album.title} failed",
                e
            )
            BuiltGroup(null, e)
        }
    }

    private fun fetchAlbumGrid(video: Video): MediaGroup? {
        val albumId = video.reloadPageKey ?: return null
        if (albumId.isEmpty()) return null

        val title = video.title ?: albumId
        val album = ImmichAlbumImpl(albumId, title, 0, null, null)
        val page = toImmichPage(
            MediaSourceRegistry.getImmichServiceManager().libraryService
                .getAlbumAssetsPageObserve(album, 0)
                .blockingFirst()
        )
        if (page == null || page.items.isEmpty()) return null
        return ImmichMediaGroupAdapter.fromAlbumGrid(album, page.items, page)
    }

    private fun fetchContinueGroup(group: ImmichMediaGroupAdapter): MediaGroup? {
        val nextPageKey = group.nextPageKey
        if (nextPageKey.isNullOrEmpty()) return null
        val offset = parseOffset(nextPageKey)
        if (offset < 0) return null

        val libraryService = MediaSourceRegistry.getImmichServiceManager().libraryService
        val page: ImmichPage? = when (group.kind) {
            ImmichMediaGroupAdapter.Kind.RECENT_VIDEOS ->
                toImmichPage(libraryService.getRecentVideosPageObserve(offset).blockingFirst())
            ImmichMediaGroupAdapter.Kind.ALBUM_ROW,
            ImmichMediaGroupAdapter.Kind.ALBUM_GRID -> {
                val album = group.immichAlbum ?: return null
                toImmichPage(
                    libraryService.getAlbumAssetsPageObserve(album, offset).blockingFirst()
                )
            }
        }

        if (page == null || page.items.isEmpty()) return null
        return ImmichMediaGroupAdapter.continueFrom(group, page.items, page)
    }

    private fun toImmichPage(page: ImmichAssetPage?): ImmichPage? {
        if (page == null) return null
        if (page is ImmichPage) return page
        val items = page.items ?: emptyList()
        return ImmichPage(items, page.offset, page.totalSize, items.size)
    }

    private fun parseOffset(nextPageKey: String): Int {
        return try {
            nextPageKey.toInt()
        } catch (_: NumberFormatException) {
            Log.e(SmartTublexApplication.TAG, "Invalid Immich nextPageKey: $nextPageKey")
            -1
        }
    }
}
