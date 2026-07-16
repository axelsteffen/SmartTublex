package de.developerleipzig.smarttublex.misc

import com.liskovsoft.mediaserviceinterfaces.ServiceManager
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager

/**
 * Central registry for media sources (YouTube, Plex).
 * SmartTublex-only; prefer this over direct [YouTubeServiceManager] / [PlexServiceManager] access.
 */
object MediaSourceRegistry {
    enum class Source {
        YOUTUBE,
        PLEX
    }

    @Volatile
    private var activeSource: Source = Source.YOUTUBE

    fun getActiveSource(): Source = activeSource

    fun setActiveSource(source: Source?) {
        if (source != null) {
            activeSource = source
        }
    }

    fun isPlexEnabled(): Boolean = true

    /**
     * MSC-shaped [ServiceManager] for YouTube. Plex uses [getPlexServiceManager] (parallel API).
     */
    fun getServiceManager(): ServiceManager = YouTubeServiceManager.instance()

    fun getPlexServiceManager(): com.liskovsoft.plexserviceinterfaces.PlexServiceManager {
        return PlexServiceManager.instance()
    }
}
