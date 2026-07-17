package de.developerleipzig.smarttublex.presenters

import android.annotation.SuppressLint
import android.content.Context
import de.developerleipzig.plexapi.PlexServiceManager
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil
import de.developerleipzig.smarttublex.browse.PlexBrowseInstaller
import de.developerleipzig.smarttublex.misc.MediaSourceRegistry
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry

/**
 * Plex sign-in, server picker, and sign-out (SmartTube [PlexSettingsPresenter] equivalent).
 *
 * Plex-specific copy is hardcoded — app resources are not merged into the wrapped APK.
 */
class PlexSettingsPresenter private constructor(context: Context) : BasePresenter<Void>(context) {

    fun unhold() {
        sInstance = null
    }

    fun show() {
        val ctx = context ?: return
        if (!MediaSourceRegistry.isPlexEnabled()) {
            MessageHelpers.showMessage(ctx, "Plex integration is not available")
            return
        }

        val settingsPresenter = AppDialogPresenter.instance(ctx)
        val signInService = PlexServiceManager.instance().signInService
        val selected = PlexServiceManager.instance().serverService.selectedServer

        if (!signInService.isSigned) {
            settingsPresenter.appendSingleButton(
                UiOptionItem.from(
                    ctx.getString(R.string.action_signin),
                    { _ ->
                        settingsPresenter.closeDialog()
                        PlexSignInPresenter.instance(ctx).start()
                    }
                )
            )
        } else {
            settingsPresenter.appendSingleButton(
                UiOptionItem.from(
                    TITLE_SELECT_SERVER,
                    { _ ->
                        settingsPresenter.closeDialog()
                        PlexServerSelectionPresenter.instance(ctx).show(true)
                    }
                )
            )

            settingsPresenter.appendSingleButton(
                UiOptionItem.from(
                    ctx.getString(R.string.dialog_remove_account),
                    { _ ->
                        AppDialogUtil.showConfirmationDialog(
                            ctx,
                            ctx.getString(R.string.dialog_remove_account)
                        ) {
                            signOut()
                            settingsPresenter.closeDialog()
                            MessageHelpers.showMessage(ctx, R.string.msg_done)
                        }
                    }
                )
            )
        }

        var title = SidebarSectionRegistry.TITLE_PLEX
        if (signInService.isSigned && selected != null && !selected.name.isNullOrEmpty()) {
            title = "Server: ${selected.name}"
        }
        settingsPresenter.showDialog(title, Runnable { unhold() })
    }

    private fun signOut() {
        val ctx = context
        PlexServiceManager.instance().signInService.signOut()
        if (ctx != null) {
            PlexBrowseInstaller.refresh(ctx)
            try {
                BrowsePresenter.instance(ctx).updateSections()
            } catch (_: Throwable) {
                // BrowsePresenter may not be ready yet
            }
        }
    }

    companion object {
        private const val TITLE_SELECT_SERVER = "Select Plex server"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sInstance: PlexSettingsPresenter? = null

        fun instance(context: Context): PlexSettingsPresenter {
            var current = sInstance
            if (current == null) {
                current = PlexSettingsPresenter(context.applicationContext)
                sInstance = current
            }
            current.setContext(context)
            return current
        }
    }
}
