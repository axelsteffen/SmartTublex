package de.developerleipzig.smarttublex

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication
import de.developerleipzig.smarttublex.browse.PlexBrowseInstaller
import de.developerleipzig.smarttublex.misc.MediaSourceRegistry
import de.developerleipzig.smarttublex.misc.PlexPlaybackInstaller
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import de.developerleipzig.smarttublex.presenters.PlexChannelUploadsPresenter

/**
 * Wrapper [MainApplication]. Override points for SmartTublex customization.
 */
class SmartTublexApplication : MainApplication() {
    override fun onCreate() {
        Log.i(TAG, "SmartTublexApplication.onCreate — wrapper starting")
        PlexServiceManager.init(this)
        MediaSourceRegistry.setActiveSource(MediaSourceRegistry.Source.YOUTUBE)
        val plex = MediaSourceRegistry.getPlexServiceManager()
        Log.i(
            TAG,
            "Plex enabled=${MediaSourceRegistry.isPlexEnabled()} " +
                "sidebarType=${SidebarSectionRegistry.TYPE_PLEX} " +
                "plexManager=${plex.javaClass.name}"
        )
        super.onCreate()
        PlexPlaybackInstaller.ensureInstalled(this)
        PlexChannelUploadsPresenter.ensureInstalled(this)
        registerActivityLifecycleCallbacks(BrowseInstallCallbacks)
        Log.i(TAG, "SmartTublexApplication.onCreate — upstream MainApplication ready")
    }

    private object BrowseInstallCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            val name = activity.javaClass.name
            if (name.endsWith(".BrowseActivity")) {
                PlexBrowseInstaller.ensureInstalled(activity)
            }
            // Re-try playback / uploads hooks if Application.onCreate ran before presenters were ready
            if (name.endsWith(".BrowseActivity") || name.endsWith(".PlaybackActivity")
                || name.endsWith(".ChannelUploadsActivity")
            ) {
                PlexPlaybackInstaller.ensureInstalled(activity)
                PlexChannelUploadsPresenter.ensureInstalled(activity)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    companion object {
        const val TAG = "SmartTublex"
    }
}
