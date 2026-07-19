package de.developerleipzig.smarttublex.misc

import android.util.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController
import de.developerleipzig.smarttublex.SmartTublexApplication

/**
 * WRAPPER: seeds Plex / Immich [MediaItemFormatInfo] into the upstream YouTube format cache
 * (network on IO thread) before [VideoLoaderController] asks YouTube for `videoId`.
 * Immich still images open [ImmichImageViewerActivity] instead of ExoPlayer.
 */
class PlexAwareVideoLoaderController : VideoLoaderController() {
    override fun onNewVideo(item: Video?) {
        when {
            item != null && PlexPlaybackBridge.isPlexVideo(item) -> {
                Log.i(
                    SmartTublexApplication.TAG,
                    "PlexAwareVideoLoaderController: preparing Plex stream videoId=${item.videoId}"
                )
                // Must finish before super: loadFormatInfo reads the YT format cache next.
                PlexPlaybackBridge.seedFormatCacheIfPlex(item)
                // WRAPPER: sync seed next episode so onPlayEnd → loadNext sees nextMediaItem
                PlexNextEpisodeResolver.seedNextEpisode(item)
            }
            item != null && ImmichPlaybackBridge.isImmichVideo(item) -> {
                // Stills are routed via ChannelUploadsPresenter.openChannel (no PlaybackPresenter).
                Log.i(
                    SmartTublexApplication.TAG,
                    "PlexAwareVideoLoaderController: preparing Immich stream videoId=${item.videoId}"
                )
                // Skip super when seed fails — avoids YouTube path + opaque Exo errors.
                if (!ImmichPlaybackBridge.seedFormatCacheIfImmich(item)) {
                    return
                }
            }
        }
        super.onNewVideo(item)
    }

    /**
     * For Plex episodes, only play the series next episode — never Continue Watching /
     * Playlist / section siblings via [SuggestionsController.getNext].
     */
    override fun loadNext() {
        val video = getVideo()
        if (video != null && PlexPlaybackBridge.isPlexVideo(video)) {
            val knownEpisode = PlexNextEpisodeResolver.isEpisode(video)
            var nextItem = video.nextMediaItem
            if (nextItem == null) {
                nextItem = PlexNextEpisodeResolver.resolveBlocking(video)
                if (nextItem != null) {
                    video.nextMediaItem = nextItem
                }
            }
            if (nextItem != null) {
                Log.i(
                    SmartTublexApplication.TAG,
                    "PlexAwareVideoLoaderController: loadNext via series next → ${nextItem.videoId}"
                )
                onSuggestionItemClicked(Video.from(nextItem))
                return
            }
            if (knownEpisode) {
                Log.i(
                    SmartTublexApplication.TAG,
                    "PlexAwareVideoLoaderController: no series next — skip shelf neighbor"
                )
                return
            }
        }
        super.loadNext()
    }
}
