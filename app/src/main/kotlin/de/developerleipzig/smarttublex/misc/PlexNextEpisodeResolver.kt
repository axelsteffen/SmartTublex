package de.developerleipzig.smarttublex.misc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import de.developerleipzig.plexapi.adapter.PlexMediaItemAdapter
import de.developerleipzig.plexapi.library.PlexMediaItemImpl
import de.developerleipzig.plexserviceinterfaces.data.PlexMediaItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import de.developerleipzig.smarttublex.SmartTublexApplication
import io.reactivex.schedulers.Schedulers

/**
 * Seeds [Video.nextMediaItem] for Plex episodes so upstream
 * [com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController.getNext]
 * can autoplay the next episode.
 *
 * Prefers the current [Video.group] (season grid); falls back to PMS children API.
 */
object PlexNextEpisodeResolver {
    private const val TYPE_EPISODE = "episode"
    private const val TYPE_SEASON = "season"

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Sets [Video.nextMediaItem] when the current item is a Plex episode.
     * Group resolution is synchronous; API fallback runs on IO without blocking the caller.
     */
    fun seedNextEpisode(video: Video?) {
        if (video == null || !PlexPlaybackBridge.isPlexVideo(video)) return
        val episode = PlexPlaybackBridge.resolvePlexItem(video) ?: return
        if (!TYPE_EPISODE.equals(episode.type, ignoreCase = true)) return

        val fromGroup = resolveFromGroup(video)
        if (fromGroup != null) {
            video.nextMediaItem = fromGroup
            Log.i(
                SmartTublexApplication.TAG,
                "PlexNextEpisodeResolver: next from group → ${fromGroup.videoId}"
            )
            return
        }

        Schedulers.io().scheduleDirect {
            try {
                val next = resolveFromApi(episode)
                if (next != null) {
                    mainHandler.post {
                        // Only set if nothing else filled it (e.g. user queue).
                        if (video.nextMediaItem == null) {
                            video.nextMediaItem = next
                            Log.i(
                                SmartTublexApplication.TAG,
                                "PlexNextEpisodeResolver: next from API → ${next.videoId}"
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(SmartTublexApplication.TAG, "PlexNextEpisodeResolver: API resolve failed", t)
            }
        }
    }

    internal fun resolveFromGroup(video: Video): MediaItem? {
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
        val library = MediaSourceRegistry.getPlexServiceManager().libraryService

        val parentKey = episode.parentRatingKey
        if (!parentKey.isNullOrEmpty()) {
            val seasonStub = stubContainer(parentKey, TYPE_SEASON)
            val siblings = library.getChildrenObserve(seasonStub).blockingFirst()
            val nextInSeason = nextAfter(siblings, episode.ratingKey, TYPE_EPISODE)
            if (nextInSeason != null) {
                return PlexMediaItemAdapter.from(nextInSeason)
            }
        }

        val showKey = episode.grandparentRatingKey
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
