package de.developerleipzig.smarttublex

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import de.developerleipzig.plexapi.PlexServiceManager
import de.developerleipzig.immichapi.ImmichServiceManager
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication
import de.developerleipzig.smarttublex.browse.ContentBrowseInstaller
import de.developerleipzig.smarttublex.misc.ImmichAuthHeaderInstaller
import de.developerleipzig.smarttublex.misc.ImmichSettingsInstaller
import de.developerleipzig.smarttublex.misc.MediaSourceRegistry
import de.developerleipzig.smarttublex.misc.PlexPlaybackInstaller
import de.developerleipzig.smarttublex.misc.PlexSettingsInstaller
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import de.developerleipzig.smarttublex.presenters.PlexChannelUploadsPresenter

/**
 * Wrapper [MainApplication]. Override points for SmartTublex customization.
 */
class SmartTublexApplication : MainApplication() {
    override fun onCreate() {
        Log.i(TAG, "SmartTublexApplication.onCreate — wrapper starting")
        PlexServiceManager.init(this)
        ImmichServiceManager.init(this)
        MediaSourceRegistry.setActiveSource(MediaSourceRegistry.Source.YOUTUBE)
        val plex = MediaSourceRegistry.getPlexServiceManager()
        val immich = MediaSourceRegistry.getImmichServiceManager()
        Log.i(
            TAG,
            "Plex enabled=${MediaSourceRegistry.isPlexEnabled()} " +
                "sidebarMovies=${SidebarSectionRegistry.TYPE_MOVIES} " +
                "plexManager=${plex.javaClass.name}"
        )
        Log.i(
            TAG,
            "Immich enabled=${MediaSourceRegistry.isImmichEnabled()} " +
                "sidebarPhotos=${SidebarSectionRegistry.TYPE_PHOTOS} " +
                "immichManager=${immich.javaClass.name}"
        )
        if (SidebarSectionRegistry.isImmichReady(this)) {
            ImmichAuthHeaderInstaller.ensureReady(this)
        }
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
                ContentBrowseInstaller.ensureInstalled(activity)
                PlexSettingsInstaller.ensureInstalled(activity)
                ImmichSettingsInstaller.ensureInstalled(activity)
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
