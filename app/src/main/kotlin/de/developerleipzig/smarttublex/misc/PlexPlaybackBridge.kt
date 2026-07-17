package de.developerleipzig.smarttublex.misc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import de.developerleipzig.plexapi.adapter.PlexMediaItemAdapter
import de.developerleipzig.plexapi.adapter.PlexMediaItemFormatInfo
import de.developerleipzig.plexapi.library.PlexMediaItemImpl
import de.developerleipzig.plexserviceinterfaces.data.PlexMediaItem
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.errors.PlexErrorClassifier
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 2.5 / 3d: resolve a Plex item to an ExoPlayer-compatible [MediaItemFormatInfo]
 * and seed the upstream YouTube format cache so [VideoLoaderController] opens Direct Play / HLS.
 */
object PlexPlaybackBridge {
    private const val SEED_TIMEOUT_SEC = 60L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isPlexVideo(video: Video?): Boolean {
        if (video == null) return false
        if (video.mediaItem is PlexMediaItemAdapter) return true
        return false
    }

    fun resolveFormatInfo(item: PlexMediaItem): Observable<MediaItemFormatInfo> {
        MediaSourceRegistry.setActiveSource(MediaSourceRegistry.Source.PLEX)
        return MediaSourceRegistry.getPlexServiceManager()
            .getMediaService()
            .getStreamInfoObserve(item)
            .map { stream ->
                val format = PlexMediaItemFormatInfo.from(item, stream)
                    ?: throw IllegalStateException("Plex stream URL empty for ${item.ratingKey}")
                format
            }
    }

    /**
     * Resolve + seed YouTube format cache. Safe to call from the main thread:
     * network work always runs on [Schedulers.io].
     *
     * @return true when a Plex format was seeded
     */
    fun seedFormatCacheIfPlex(video: Video?): Boolean {
        val item = resolvePlexItem(video) ?: return false
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return seedFormatCacheOnCurrentThread(video, item)
        }
        val ok = AtomicBoolean(false)
        val error = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Schedulers.io().scheduleDirect {
            try {
                ok.set(seedFormatCacheOnCurrentThread(video, item))
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
                    "PlexPlaybackBridge: seed timed out for ratingKey=${item.ratingKey}"
                )
                showPlaybackError(SocketTimeoutException("Plex stream resolve timed out"))
                false
            } else {
                error.get()?.let {
                    Log.e(
                        SmartTublexApplication.TAG,
                        "PlexPlaybackBridge: resolve failed for ratingKey=${item.ratingKey}",
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
                "PlexPlaybackBridge: seed interrupted for ratingKey=${item.ratingKey}",
                t
            )
            false
        }
    }

    private fun seedFormatCacheOnCurrentThread(video: Video?, item: PlexMediaItem): Boolean {
        return try {
            MediaSourceRegistry.setActiveSource(MediaSourceRegistry.Source.PLEX)
            val stream = MediaSourceRegistry.getPlexServiceManager()
                .getMediaService()
                .getStreamInfoObserve(item)
                .blockingFirst()
            applyViewOffset(video, stream.viewOffsetMs)
            val format = PlexMediaItemFormatInfo.from(item, stream)
            if (format == null) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "PlexPlaybackBridge: empty stream URL for ratingKey=${item.ratingKey}"
                )
                showPlaybackError(IllegalStateException("Plex stream URL empty"))
                return false
            }
            seedYouTubeFormatCache(format)
            Log.i(
                SmartTublexApplication.TAG,
                "PlexPlaybackBridge: seeded format cache ratingKey=${item.ratingKey} " +
                    "hls=${format.containsHlsUrl()} urls=${format.containsUrlFormats()}"
            )
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "PlexPlaybackBridge: resolve failed for ratingKey=${item.ratingKey}",
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
        val message = PlexErrorClassifier.playbackMessage(error)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            MessageHelpers.showMessage(context, message)
        } else {
            mainHandler.post { MessageHelpers.showMessage(context, message) }
        }
    }

    fun resolvePlexItem(video: Video?): PlexMediaItem? {
        if (video == null) return null
        val mediaItem = video.mediaItem
        if (mediaItem is PlexMediaItemAdapter) {
            return mediaItem.plexItem
        }
        val ratingKey = video.videoId
        if (ratingKey.isNullOrEmpty()) {
            return null
        }
        // Queue / restore paths may drop the adapter; stub is enough for stream lookup by ratingKey.
        if (!isLikelyPlexRatingKey(ratingKey)) {
            return null
        }
        return PlexMediaItemImpl(
            ratingKey,
            null,
            video.title,
            "movie",
            if (video.getDurationMs() > 0) video.getDurationMs() else 0L,
            video.cardImageUrl,
            0
        )
    }

    /**
     * YouTube IDs are 11-char base64-ish; Plex ratingKeys are decimal (often short).
     * Only used when mediaItem was not retained — avoids treating YT ids as Plex.
     */
    private fun isLikelyPlexRatingKey(videoId: String): Boolean {
        if (videoId.length > 18) return false
        return videoId.all { it.isDigit() }
    }

    private fun applyViewOffset(video: Video?, viewOffsetMs: Long) {
        if (video == null || viewOffsetMs <= 0L) return
        if (video.getPositionMs() > 0L || video.startTimeSeconds > 0) return
        video.startTimeSeconds = (viewOffsetMs / 1000L).toInt()
    }

    private fun seedYouTubeFormatCache(format: MediaItemFormatInfo) {
        val service = YouTubeMediaItemService.instance()
        val field = YouTubeMediaItemService::class.java.getDeclaredField("mCachedFormatInfo")
        field.isAccessible = true
        field.set(service, format)
    }
}
