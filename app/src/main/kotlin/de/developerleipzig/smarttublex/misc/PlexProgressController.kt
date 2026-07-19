package de.developerleipzig.smarttublex.misc

import android.util.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController
import com.liskovsoft.smartyoutubetv2.common.utils.Utils
import de.developerleipzig.plexserviceinterfaces.PlexMediaService
import de.developerleipzig.smarttublex.SmartTublexApplication

/**
 * WRAPPER: reports Plex playback progress to PMS (`/:/timeline`) so Continue Watching
 * and resume offsets survive app restarts. Upstream [VideoStateController] only syncs
 * YouTube history against the plain APK artifact.
 */
class PlexProgressController : BasePlayerController() {
    private var playEnabled = true
    private val reportRunnable = Runnable { reportProgress() }

    override fun onNewVideo(item: Video?) {
        val player = getPlayer()
        if (player != null && player.containsMedia()) {
            reportProgress()
        }
        playEnabled = true
    }

    override fun onEngineReleased() {
        val player = getPlayer() ?: return
        if (player.containsMedia()) {
            playEnabled = player.getPlayWhenReady()
            reportProgress()
        }
    }

    override fun onTickle() {
        val player = getPlayer() ?: return
        if (!player.isEngineInitialized || !player.isPlaying) {
            return
        }
        val video = getVideo()
        if (video != null && PlexPlaybackBridge.isPlexVideo(video)) {
            reportProgress()
        }
    }

    override fun onPlay() {
        playEnabled = true
        Utils.removeCallbacks(reportRunnable)
    }

    override fun onPause() {
        playEnabled = false
        // Short delay: seek bursts often pause/play; still flush before app kill.
        Utils.postDelayed(reportRunnable, PAUSE_REPORT_DELAY_MS)
    }

    override fun onPlayEnd() {
        reportProgress()
    }

    override fun onSeekPositionChanged(positionMs: Long) {
        Utils.post(reportRunnable)
    }

    override fun onPreviousClicked(): Boolean {
        val player = getPlayer()
        if (player != null && player.getPositionMs() > BEGIN_THRESHOLD_MS) {
            reportProgress()
        }
        return false
    }

    override fun onNextClicked(): Boolean {
        reportProgress()
        return false
    }

    override fun onSuggestionItemClicked(item: Video?) {
        reportProgress()
    }

    override fun onEngineError(type: Int, rendererIndex: Int, error: Throwable?) {
        reportProgress()
    }

    private fun reportProgress() {
        val player = getPlayer() ?: return
        val video = getVideo() ?: return
        if (!PlexPlaybackBridge.isPlexVideo(video)) {
            return
        }

        var positionMs = player.getPositionMs().coerceAtLeast(0L)
        var durationMs = player.getDurationMs()
        if (durationMs <= 0L) {
            durationMs = video.getDurationMs()
        }

        val state: String
        if (durationMs > 0L && durationMs - positionMs <= END_THRESHOLD_MS) {
            // Near end → stopped so PMS can scrobble as watched.
            positionMs = durationMs
            state = PlexMediaService.STATE_STOPPED
        } else if (player.isPlaying) {
            state = PlexMediaService.STATE_PLAYING
        } else {
            state = if (playEnabled) {
                PlexMediaService.STATE_PAUSED
            } else {
                PlexMediaService.STATE_STOPPED
            }
        }

        Log.d(
            SmartTublexApplication.TAG,
            "PlexProgressController: report ratingKey=${video.videoId} " +
                "pos=$positionMs/$durationMs state=$state"
        )
        PlexPlaybackBridge.updateProgress(video, positionMs, durationMs, state)
    }

    companion object {
        private const val BEGIN_THRESHOLD_MS = 10_000L
        private const val END_THRESHOLD_MS = 1_000L
        private const val PAUSE_REPORT_DELAY_MS = 1_000L
    }
}
