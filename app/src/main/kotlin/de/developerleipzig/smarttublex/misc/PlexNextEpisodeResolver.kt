package de.developerleipzig.smarttublex.misc

import android.os.Looper
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import de.developerleipzig.plexapi.adapter.PlexMediaGroupAdapter
import de.developerleipzig.plexapi.adapter.PlexMediaItemAdapter
import de.developerleipzig.plexapi.library.PlexMediaItemImpl
import de.developerleipzig.plexserviceinterfaces.data.PlexMediaItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import de.developerleipzig.smarttublex.SmartTublexApplication
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Seeds [Video.nextMediaItem] for Plex episodes so upstream autoplay can continue the series.
 *
 * Prefers the current [Video.group] only when it is a season/episode container;
 * falls back to PMS children API for Continue Watching and other shelves.
 * Resolution is synchronous (IO + latch) so [nextMediaItem] is set before playback continues.
 */
object PlexNextEpisodeResolver {
    private const val TYPE_EPISODE = "episode"
    private const val TYPE_SEASON = "season"
    private const val RESOLVE_TIMEOUT_SEC = 10L

    fun isEpisode(video: Video?): Boolean {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video)) return false
        val item = PlexPlaybackBridge.resolvePlexItem(video) ?: return false
        return TYPE_EPISODE.equals(item.type, ignoreCase = true)
    }

    /**
     * Sets [Video.nextMediaItem] when the current item is a Plex episode.
     * Blocks until resolved (or timeout) so end-of-play always sees a stable value.
     */
    fun seedNextEpisode(video: Video?) {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video)) return
        val item = PlexPlaybackBridge.resolvePlexItem(video) ?: return
        // Skip known non-episodes (movies); unknown type still tries resolve (refresh on IO).
        if (item.type != null && !TYPE_EPISODE.equals(item.type, ignoreCase = true)) {
            return
        }
        val next = resolveBlocking(video) ?: run {
            Log.i(SmartTublexApplication.TAG, "PlexNextEpisodeResolver: no next episode")
            return
        }
        video.nextMediaItem = next
        Log.i(
            SmartTublexApplication.TAG,
            "PlexNextEpisodeResolver: seeded next → ${next.videoId}"
        )
    }

    /**
     * Group path (season grid) or PMS children API. Safe to call from the main thread:
     * network work runs on [Schedulers.io] with a latch.
     */
    fun resolveBlocking(video: Video?): MediaItem? {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video)) return null

        val fromGroup = resolveFromGroup(video)
        if (fromGroup != null) {
            return fromGroup
        }

        val seed = PlexPlaybackBridge.resolvePlexItem(video) ?: return null

        if (Looper.myLooper() != Looper.getMainLooper()) {
            return resolveFromApi(seed)
        }

        val result = AtomicReference<MediaItem?>(null)
        val error = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Schedulers.io().scheduleDirect {
            try {
                result.set(resolveFromApi(seed))
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        return try {
            if (!latch.await(RESOLVE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                Log.w(
                    SmartTublexApplication.TAG,
                    "PlexNextEpisodeResolver: resolve timed out for ${seed.ratingKey}"
                )
                null
            } else {
                error.get()?.let {
                    Log.w(SmartTublexApplication.TAG, "PlexNextEpisodeResolver: resolve failed", it)
                }
                result.get()
            }
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(SmartTublexApplication.TAG, "PlexNextEpisodeResolver: resolve interrupted", t)
            null
        }
    }

    internal fun resolveFromGroup(video: Video): MediaItem? {
        // Only season/episode grids — Continue Watching / Recently Added shelves must not
        // supply the "next" sibling (that would play the next On Deck title).
        val mediaGroup = video.group?.mediaGroup
        if (mediaGroup !is PlexMediaGroupAdapter || !mediaGroup.isContainerGroup) {
            return null
        }

        val videos = video.group?.videos ?: return null
        val currentId = video.videoId ?: return null
        val start = videos.indexOfFirst { it.videoId == currentId }
        if (start < 0) return null
        for (i in start + 1 until videos.size) {
            val candidate = videos[i].mediaItem
            if (candidate is PlexMediaItemAdapter
                && TYPE_EPISODE.equals(candidate.plexItem.type, ignoreCase = true)
            ) {
                return candidate
            }
        }
        return null
    }

    internal fun resolveFromApi(episode: PlexMediaItem): MediaItem? {
        var resolved = episode
        if (!TYPE_EPISODE.equals(resolved.type, ignoreCase = true)
            || resolved.parentRatingKey.isNullOrEmpty()
            || resolved.grandparentRatingKey.isNullOrEmpty()
        ) {
            val refreshed = refreshItem(resolved.ratingKey)
            if (refreshed != null) {
                resolved = refreshed
            }
        }
        if (!TYPE_EPISODE.equals(resolved.type, ignoreCase = true)) {
            return null
        }

        val library = MediaSourceRegistry.getPlexServiceManager().libraryService

        val parentKey = resolved.parentRatingKey
        if (!parentKey.isNullOrEmpty()) {
            val seasonStub = stubContainer(parentKey, TYPE_SEASON)
            val siblings = library.getChildrenObserve(seasonStub).blockingFirst()
            val nextInSeason = nextAfter(siblings, resolved.ratingKey, TYPE_EPISODE)
            if (nextInSeason != null) {
                return PlexMediaItemAdapter.from(nextInSeason)
            }
        }

        val showKey = resolved.grandparentRatingKey
        if (showKey.isNullOrEmpty() || parentKey.isNullOrEmpty()) {
            return null
        }

        val showStub = stubContainer(showKey, "show")
        val seasons = library.getChildrenObserve(showStub).blockingFirst()
            ?.filter { TYPE_SEASON.equals(it.type, ignoreCase = true) }
            ?.sortedWith(compareBy({ it.index }, { it.ratingKey }))
            ?: return null

        val currentSeasonIdx = seasons.indexOfFirst { it.ratingKey == parentKey }
        if (currentSeasonIdx < 0 || currentSeasonIdx >= seasons.lastIndex) {
            return null
        }

        for (s in currentSeasonIdx + 1 until seasons.size) {
            val nextSeason = seasons[s]
            val episodes = library.getChildrenObserve(nextSeason).blockingFirst()
                ?.filter { TYPE_EPISODE.equals(it.type, ignoreCase = true) }
                ?.sortedWith(compareBy({ it.index }, { it.ratingKey }))
            val first = episodes?.firstOrNull() ?: continue
            return PlexMediaItemAdapter.from(first)
        }
        return null
    }

    private fun refreshItem(ratingKey: String?): PlexMediaItem? {
        if (ratingKey.isNullOrEmpty()) return null
        return try {
            MediaSourceRegistry.getPlexServiceManager()
                .libraryService
                .getItemObserve(ratingKey)
                .blockingFirst()
        } catch (t: Throwable) {
            Log.w(
                SmartTublexApplication.TAG,
                "PlexNextEpisodeResolver: metadata refresh failed for $ratingKey",
                t
            )
            null
        }
    }

    private fun nextAfter(
        items: List<PlexMediaItem>?,
        currentRatingKey: String?,
        type: String
    ): PlexMediaItem? {
        if (items.isNullOrEmpty() || currentRatingKey.isNullOrEmpty()) return null
        val filtered = items
            .filter { type.equals(it.type, ignoreCase = true) }
            .sortedWith(compareBy({ it.index }, { it.ratingKey }))
        val idx = filtered.indexOfFirst { it.ratingKey == currentRatingKey }
        if (idx < 0 || idx >= filtered.lastIndex) return null
        return filtered[idx + 1]
    }

    private fun stubContainer(ratingKey: String, type: String): PlexMediaItem {
        return PlexMediaItemImpl(ratingKey, "/library/metadata/$ratingKey", null, type, 0L, null, 0)
    }
}
