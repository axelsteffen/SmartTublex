package de.developerleipzig.smarttublex.misc

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.plexapi.adapter.PlexMediaItemFormatInfo
import com.liskovsoft.plexserviceinterfaces.data.PlexMediaItem
import io.reactivex.Observable

/**
 * Phase 2.5: resolve a Plex item to an ExoPlayer-compatible [MediaItemFormatInfo]
 * via PlexServiceCore stream APIs + [PlexMediaItemFormatInfo] adapter.
 */
object PlexPlaybackBridge {
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
}
