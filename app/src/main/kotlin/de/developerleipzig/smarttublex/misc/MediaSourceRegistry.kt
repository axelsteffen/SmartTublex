package de.developerleipzig.smarttublex.misc

import com.liskovsoft.mediaserviceinterfaces.ServiceManager
import de.developerleipzig.immichapi.ImmichServiceManager
import de.developerleipzig.plexapi.PlexServiceManager
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager

/**
 * Central registry for media sources (YouTube, Plex, Immich).
 * SmartTublex-only; prefer this over direct service-manager access.
 */
object MediaSourceRegistry {
    enum class Source {
        YOUTUBE,
        PLEX,
        IMMICH
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

    fun isImmichEnabled(): Boolean = true

    /**
     * MSC-shaped [ServiceManager] for YouTube.
     * Plex / Immich use their parallel APIs via [getPlexServiceManager] / [getImmichServiceManager].
     */
    fun getServiceManager(): ServiceManager = YouTubeServiceManager.instance()

    fun getPlexServiceManager(): de.developerleipzig.plexserviceinterfaces.PlexServiceManager {
        return PlexServiceManager.instance()
    }

    fun getImmichServiceManager(): de.developerleipzig.immichserviceinterfaces.ImmichServiceManager {
        return ImmichServiceManager.instance()
    }
}
