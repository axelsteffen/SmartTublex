package de.developerleipzig.smarttublex.misc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import de.developerleipzig.immichapi.adapter.ImmichMediaItemAdapter
import de.developerleipzig.immichapi.adapter.ImmichMediaItemFormatInfo
import de.developerleipzig.immichapi.library.ImmichAssetImpl
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService
import de.developerleipzig.smarttublex.SmartTublexApplication
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 3f: resolve an Immich asset to ExoPlayer [MediaItemFormatInfo], seed the upstream
 * YouTube format cache, and ensure `x-api-key` is attached via [ImmichAuthHeaderInstaller].
 */
object ImmichPlaybackBridge {
    private const val SEED_TIMEOUT_SEC = 60L
    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isImmichVideo(video: Video?): Boolean {
        if (video == null) return false
        val mediaItem = video.mediaItem
        if (mediaItem is ImmichMediaItemAdapter) {
            return mediaItem.immichAsset != null
        }
        return false
    }

    fun resolveFormatInfo(asset: ImmichAsset): Observable<MediaItemFormatInfo> {
        MediaSourceRegistry.setActiveSource(MediaSourceRegistry.Source.IMMICH)
        return MediaSourceRegistry.getImmichServiceManager()
            .mediaService
            .getStreamInfoObserve(asset)
            .map { stream ->
                ImmichMediaItemFormatInfo.from(asset, stream)
                    ?: throw IllegalStateException("Immich stream URL empty for ${asset.id}")
            }
    }

    /**
     * Resolve + seed YouTube format cache. Safe to call from the main thread:
     * network work always runs on [Schedulers.io].
     *
     * @return true when an Immich format was seeded
     */
    fun seedFormatCacheIfImmich(video: Video?): Boolean {
        val asset = resolveImmichAsset(video) ?: return false
        val context = try {
            GlobalPreferences.context()
        } catch (_: Throwable) {
            null
        }
        if (context != null) {
            ImmichAuthHeaderInstaller.ensureReady(context)
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            return seedFormatCacheOnCurrentThread(asset)
        }
        val ok = AtomicBoolean(false)
        val error = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Schedulers.io().scheduleDirect {
            try {
                ok.set(seedFormatCacheOnCurrentThread(asset))
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        return try {
            if (!latch.await(SEED_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "ImmichPlaybackBridge: seed timed out for assetId=${asset.id}"
                )
                showPlaybackError(SocketTimeoutException("Immich stream resolve timed out"))
                false
            } else {
                error.get()?.let {
                    Log.e(
                        SmartTublexApplication.TAG,
                        "ImmichPlaybackBridge: resolve failed for assetId=${asset.id}",
                        it
                    )
                    showPlaybackError(it)
                }
                ok.get()
            }
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(
                SmartTublexApplication.TAG,
                "ImmichPlaybackBridge: seed interrupted for assetId=${asset.id}",
                t
            )
            false
        }
    }

    private fun seedFormatCacheOnCurrentThread(asset: ImmichAsset): Boolean {
        return try {
            MediaSourceRegistry.setActiveSource(MediaSourceRegistry.Source.IMMICH)
            val stream = MediaSourceRegistry.getImmichServiceManager()
                .mediaService
                .getStreamInfoObserve(asset)
                .blockingFirst()
            val format = ImmichMediaItemFormatInfo.from(asset, stream)
            if (format == null) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "ImmichPlaybackBridge: empty stream URL for assetId=${asset.id}"
                )
                showPlaybackError(IllegalStateException("Immich stream URL empty"))
                return false
            }
            seedYouTubeFormatCache(format)
            Log.i(
                SmartTublexApplication.TAG,
                "ImmichPlaybackBridge: seeded format cache assetId=${asset.id} " +
                    "urls=${format.containsUrlFormats()}"
            )
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "ImmichPlaybackBridge: resolve failed for assetId=${asset.id}",
                t
            )
            showPlaybackError(t)
            false
        }
    }

    private fun showPlaybackError(error: Throwable) {
        val context = try {
            GlobalPreferences.context()
        } catch (_: Throwable) {
            null
        } ?: return
        val message = error.message?.takeIf { it.isNotEmpty() }
            ?: "Immich playback failed"
        if (Looper.myLooper() == Looper.getMainLooper()) {
            MessageHelpers.showMessage(context, message)
        } else {
            mainHandler.post { MessageHelpers.showMessage(context, message) }
        }
    }

    fun resolveImmichAsset(video: Video?): ImmichAsset? {
        if (video == null) return null
        val mediaItem = video.mediaItem
        if (mediaItem is ImmichMediaItemAdapter) {
            return mediaItem.immichAsset
        }
        val assetId = video.videoId
        if (assetId.isNullOrEmpty() || !isLikelyImmichAssetId(assetId)) {
            return null
        }
        // Queue / restore may drop the adapter; stub is enough for stream lookup by id.
        return ImmichAssetImpl(
            assetId,
            video.title,
            ImmichAsset.TYPE_VIDEO,
            if (video.getDurationMs() > 0) video.getDurationMs() else 0L,
            video.cardImageUrl,
            null
        )
    }

    /** Immich asset ids are UUIDs — avoids treating YouTube / Plex ids as Immich. */
    private fun isLikelyImmichAssetId(videoId: String): Boolean = uuidRegex.matches(videoId)

    private fun seedYouTubeFormatCache(format: MediaItemFormatInfo) {
        val service = YouTubeMediaItemService.instance()
        val field = YouTubeMediaItemService::class.java.getDeclaredField("mCachedFormatInfo")
        field.isAccessible = true
        field.set(service, format)
    }
}
