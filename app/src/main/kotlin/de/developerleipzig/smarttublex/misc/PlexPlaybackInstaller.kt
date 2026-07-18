package de.developerleipzig.smarttublex.misc

import android.content.Context
import android.util.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
import de.developerleipzig.smarttublex.SmartTublexApplication

/**
 * Phase 3d / 3f: swap upstream [VideoLoaderController] for [PlexAwareVideoLoaderController]
 * (Plex + Immich seed) inside [PlaybackPresenter]'s listener list (no upstream bytecode patch).
 */
object PlexPlaybackInstaller {
    @Volatile
    private var installed = false

    fun ensureInstalled(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val presenter = try {
                PlaybackPresenter.instance(context.applicationContext)
            } catch (t: Throwable) {
                Log.w(SmartTublexApplication.TAG, "PlexPlaybackInstaller: PlaybackPresenter not ready", t)
                return
            }
            if (!replaceVideoLoader(presenter)) {
                return
            }
            installed = true
            Log.i(SmartTublexApplication.TAG, "PlexPlaybackInstaller: PlexAwareVideoLoaderController installed")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun replaceVideoLoader(presenter: PlaybackPresenter): Boolean {
        return try {
            val field = PlaybackPresenter::class.java.getDeclaredField("mEventListeners")
            field.isAccessible = true
            val listeners = field.get(presenter) as MutableList<PlayerEventListener>

            val index = listeners.indexOfFirst {
                it is VideoLoaderController && it !is PlexAwareVideoLoaderController
            }
            if (index < 0) {
                if (listeners.any { it is PlexAwareVideoLoaderController }) {
                    return true
                }
                Log.e(
                    SmartTublexApplication.TAG,
                    "PlexPlaybackInstaller: VideoLoaderController not found in mEventListeners"
                )
                return false
            }

            val replacement = PlexAwareVideoLoaderController()
            replacement.setMainController(presenter)
            listeners[index] = replacement
            true
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexPlaybackInstaller: failed to replace VideoLoaderController", t)
            false
        }
    }
}
