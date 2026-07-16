package de.developerleipzig.smarttublex.presenters

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.plexserviceinterfaces.data.PlexServer
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.browse.PlexBrowseInstaller
import de.developerleipzig.smarttublex.errors.PlexErrorClassifier
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import io.reactivex.disposables.Disposable

/**
 * Phase 3b: pick a Plex Media Server after PIN auth (AppDialog radio list).
 */
class PlexServerSelectionPresenter private constructor(context: Context) :
    BasePresenter<Void>(context) {

    private var loadAction: Disposable? = null

    fun unhold() {
        RxHelper.disposeActions(loadAction)
        sInstance = null
    }

    /** @param force unused; kept for symmetry with fork API. */
    @Suppress("UNUSED_PARAMETER")
    fun show(force: Boolean) {
        LoadingManager.showLoading(context, true)
        val serverService = PlexServiceManager.instance().serverService
        loadAction = RxHelper.fromCallable {
            serverService.serversObserve.blockingFirst()
        }.subscribe(
            { servers -> createAndShowDialog(servers) },
            { error ->
                LoadingManager.showLoading(context, false)
                Log.e(SmartTublexApplication.TAG, "Plex server discovery failed", error)
                MessageHelpers.showMessage(
                    context,
                    PlexErrorClassifier.browseMessage(error)
                )
            }
        )
    }

    fun show() = show(false)

    private fun createAndShowDialog(servers: List<PlexServer>?) {
        LoadingManager.showLoading(context, false)
        if (servers.isNullOrEmpty()) {
            MessageHelpers.showMessage(context, "No Plex servers found")
            return
        }

        val dialogPresenter = AppDialogPresenter.instance(context)
        val optionItems = ArrayList<OptionItem>()
        val selected = PlexServiceManager.instance().serverService.selectedServer
        val selectedId = selected?.clientIdentifier

        for (server in servers) {
            val checked = selectedId != null && selectedId == server.clientIdentifier
            optionItems.add(
                UiOptionItem.from(
                    formatServer(server),
                    { _ ->
                        selectServer(server)
                        dialogPresenter.closeDialog()
                    },
                    checked
                )
            )
        }

        dialogPresenter.appendRadioCategory("Select Plex server", optionItems)
        dialogPresenter.showDialog("Select Plex server", Runnable { unhold() })
    }

    private fun selectServer(server: PlexServer) {
        PlexServiceManager.instance().serverService.selectServer(server)
        Log.i(SmartTublexApplication.TAG, "Selected Plex server: ${server.name}")
        val ctx = context ?: return
        PlexBrowseInstaller.refresh(ctx)
        val browsePresenter = BrowsePresenter.instance(ctx)
        browsePresenter.selectSection(SidebarSectionRegistry.TYPE_PLEX)
        MessageHelpers.showMessage(ctx, "Plex server selected")
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sInstance: PlexServerSelectionPresenter? = null

        fun instance(context: Context): PlexServerSelectionPresenter {
            var current = sInstance
            if (current == null) {
                current = PlexServerSelectionPresenter(context.applicationContext)
                sInstance = current
            }
            current.setContext(context)
            return current
        }

        private fun formatServer(server: PlexServer): String {
            val name = server.name ?: "Plex"
            val sb = StringBuilder(name)
            if (!server.baseUrl.isNullOrEmpty()) {
                sb.append('\n').append(server.baseUrl)
            }
            if (!server.isOnline) {
                sb.append(" (offline)")
            }
            return sb.toString()
        }
    }
}
