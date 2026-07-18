package de.developerleipzig.smarttublex.misc

import android.util.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController
import de.developerleipzig.smarttublex.SmartTublexApplication

/**
 * WRAPPER: seeds Plex / Immich [MediaItemFormatInfo] into the upstream YouTube format cache
 * (network on IO thread) before [VideoLoaderController] asks YouTube for `videoId`.
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
            }
            item != null && ImmichPlaybackBridge.isImmichVideo(item) -> {
                Log.i(
                    SmartTublexApplication.TAG,
                    "PlexAwareVideoLoaderController: preparing Immich stream videoId=${item.videoId}"
                )
                ImmichPlaybackBridge.seedFormatCacheIfImmich(item)
            }
        }
        super.onNewVideo(item)
    }
}
