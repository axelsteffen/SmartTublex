package de.developerleipzig.smarttublex.presenters

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.sharedutils.mylogger.Log as SoftLog
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView
import com.liskovsoft.smartyoutubetv2.common.misc.BrowseProcessorManager
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.PlexPlaybackBridge
import io.reactivex.Observable
import io.reactivex.disposables.Disposable

/**
 * Phase 3.4 / Immich 3d: Plex + Immich grids via upstream [ChannelUploadsView].
 *
 * Upstream [ChannelUploadsPresenter.obtainUploadsObservable] only knows YouTube reload keys.
 * We install this subclass as `sInstance` so browse-stub clicks load wrapper grids.
 */
class PlexChannelUploadsPresenter private constructor(context: Context) :
    ChannelUploadsPresenter(context) {

    override fun obtainUploadsObservable(item: Video?): Observable<MediaGroup>? {
        if (item == null) return null

        PlexBrowsePresenter.getLibraryGridObserve(item)?.let { return it }
        PlexBrowsePresenter.getChildrenGroupObserve(item)?.let { return it }
        ImmichBrowsePresenter.getAlbumGridObserve(item)?.let { return it }

        return super.obtainUploadsObservable(item)
    }

    override fun onScrollEnd(item: Video?) {
        if (item == null) {
            SoftLog.e(TAG, "Can't scroll. Video is null.")
            return
        }
        val group = item.group
        if (group == null) {
            SoftLog.e(TAG, "Can't scroll. VideoGroup is null.")
            return
        }

        if (PlexBrowsePresenter.isPlexGroup(group.mediaGroup)) {
            SoftLog.d(TAG, "onScrollEnd: Plex group title: " + group.title)
            if (!isScrollInProgress()) {
                continueWrapperGroup(group, PlexBrowsePresenter.continueGroupObserve(group.mediaGroup))
            }
            return
        }

        if (ImmichBrowsePresenter.isImmichGroup(group.mediaGroup)) {
            SoftLog.d(TAG, "onScrollEnd: Immich group title: " + group.title)
            if (!isScrollInProgress()) {
                continueWrapperGroup(group, ImmichBrowsePresenter.continueGroupObserve(group.mediaGroup))
            }
            return
        }

        super.onScrollEnd(item)
    }

    override fun openChannel(item: Video?) {
        // WRAPPER: open Plex library browse stubs even if upstream nested/playlist guards miss
        if (item != null &&
            PlexPlaybackBridge.isPlexVideo(item) &&
            item.hasReloadPageKey() &&
            !item.hasNestedItems() &&
            !item.hasPlaylist()
        ) {
            openWrapperChannel(item)
            return
        }
        // WRAPPER: Immich album browse stubs (reloadPageKey = album id; may also set playlistId)
        if (item != null &&
            ImmichBrowsePresenter.isImmichVideo(item) &&
            item.hasReloadPageKey()
        ) {
            openWrapperChannel(item)
            return
        }
        super.openChannel(item)
    }

    private fun openWrapperChannel(item: Video) {
        clear()
        channel = item
        viewManager.startView(ChannelUploadsView::class.java)
        if (view != null) {
            updateFromObservable(obtainUploadsObservable(item))
        }
    }

    private fun continueWrapperGroup(group: VideoGroup, continuation: Observable<MediaGroup>?) {
        val uploadsView = view as? ChannelUploadsView
        if (uploadsView == null) {
            SoftLog.e(TAG, "Can't continue group. The view is null.")
            return
        }

        SoftLog.d(TAG, "continueGroup: start continue group: " + group.title)
        uploadsView.showProgressBar(true)

        if (continuation == null) {
            uploadsView.showProgressBar(false)
            return
        }

        invokeDisposeActions()
        val disposable = continuation.subscribe(
            { continueMediaGroup ->
                val currentView = view as? ChannelUploadsView
                if (continueMediaGroup == null) {
                    currentView?.showProgressBar(false)
                    return@subscribe
                }
                val newGroup = VideoGroup.from(group, continueMediaGroup)
                currentView?.update(newGroup)
                browseProcessor()?.process(newGroup)
            },
            { error ->
                SoftLog.e(TAG, "continueGroup error: %s", error.message)
                (view as? ChannelUploadsView)?.showProgressBar(false)
            },
            { (view as? ChannelUploadsView)?.showProgressBar(false) }
        )
        setScrollAction(disposable)
    }

    private fun updateFromObservable(group: Observable<MediaGroup>?) {
        val uploadsView = view as? ChannelUploadsView ?: return
        if (group == null) return

        SoftLog.d(TAG, "update: Start loading a wrapper group...")
        invokeDisposeActions()
        uploadsView.showProgressBar(true)
        val disposable = group.subscribe(
            { mediaGroup -> update(mediaGroup) },
            { error ->
                SoftLog.e(TAG, "update error: %s", error.message)
                (view as? ChannelUploadsView)?.showProgressBar(false)
            },
            { (view as? ChannelUploadsView)?.showProgressBar(false) }
        )
        setUpdateAction(disposable)
    }

    private fun isScrollInProgress(): Boolean {
        val action = getField("mScrollAction") as? Disposable ?: return false
        return !action.isDisposed
    }

    private fun browseProcessor(): BrowseProcessorManager? =
        getField("mBrowseProcessor") as? BrowseProcessorManager

    private fun invokeDisposeActions() {
        try {
            val method = ChannelUploadsPresenter::class.java.getDeclaredMethod("disposeActions")
            method.isAccessible = true
            method.invoke(this)
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexChannelUploadsPresenter: disposeActions failed", t)
        }
    }

    private fun setScrollAction(disposable: Disposable) {
        setField("mScrollAction", disposable)
    }

    private fun setUpdateAction(disposable: Disposable) {
        setField("mUpdateAction", disposable)
    }

    private fun getField(name: String): Any? {
        return try {
            val field = ChannelUploadsPresenter::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.get(this)
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexChannelUploadsPresenter: get $name failed", t)
            null
        }
    }

    private fun setField(name: String, value: Any?) {
        try {
            val field = ChannelUploadsPresenter::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(this, value)
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexChannelUploadsPresenter: set $name failed", t)
        }
    }

    companion object {
        private val TAG = PlexChannelUploadsPresenter::class.java.simpleName

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sInstance: PlexChannelUploadsPresenter? = null

        fun instance(context: Context): PlexChannelUploadsPresenter {
            var current = sInstance
            if (current == null) {
                current = PlexChannelUploadsPresenter(context.applicationContext)
                sInstance = current
            }
            current.setContext(context)
            return current
        }

        /** Point upstream callers at this presenter. */
        fun ensureInstalled(context: Context) {
            val presenter = instance(context)
            try {
                val field = ChannelUploadsPresenter::class.java.getDeclaredField("sInstance")
                field.isAccessible = true
                val current = field.get(null)
                if (current === presenter) return
                field.set(null, presenter)
                Log.i(
                    SmartTublexApplication.TAG,
                    "PlexChannelUploadsPresenter: installed as ChannelUploadsPresenter.sInstance"
                )
            } catch (t: Throwable) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "Failed to install PlexChannelUploadsPresenter as ChannelUploadsPresenter.sInstance",
                    t
                )
            }
        }
    }
}
