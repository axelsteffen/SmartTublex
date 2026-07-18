package de.developerleipzig.smarttublex.presenters

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import de.developerleipzig.immichapi.ImmichServiceManager
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil
import de.developerleipzig.smarttublex.browse.ImmichBrowseInstaller
import de.developerleipzig.smarttublex.misc.ImmichAuthHeaderInstaller
import de.developerleipzig.smarttublex.misc.MediaSourceRegistry
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry

/**
 * Immich sign-in / change credentials / sign-out (mirrors [PlexSettingsPresenter]).
 *
 * Immich-specific copy is hardcoded — app resources are not merged into the wrapped APK.
 */
class ImmichSettingsPresenter private constructor(context: Context) : BasePresenter<Void>(context) {

    fun unhold() {
        sInstance = null
    }

    fun show() {
        val ctx = context ?: return
        if (!MediaSourceRegistry.isImmichEnabled()) {
            MessageHelpers.showMessage(ctx, "Immich integration is not available")
            return
        }

        val settingsPresenter = AppDialogPresenter.instance(ctx)
        val signInService = ImmichServiceManager.instance().signInService

        if (!signInService.isSigned) {
            settingsPresenter.appendSingleButton(
                UiOptionItem.from(
                    ctx.getString(R.string.action_signin),
                    { _ ->
                        settingsPresenter.closeDialog()
                        ImmichSignInPresenter.instance(ctx).start()
                    }
                )
            )
        } else {
            settingsPresenter.appendSingleButton(
                UiOptionItem.from(
                    TITLE_CHANGE_CREDENTIALS,
                    { _ ->
                        settingsPresenter.closeDialog()
                        ImmichSignInPresenter.instance(ctx).start()
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

        var title = SidebarSectionRegistry.TITLE_IMMICH
        if (signInService.isSigned) {
            val host = hostLabel(signInService.serverUrl)
            if (!host.isNullOrEmpty()) {
                title = "Server: $host"
            }
        }
        settingsPresenter.showDialog(title, Runnable { unhold() })
    }

    private fun signOut() {
        val ctx = context
        ImmichServiceManager.instance().signInService.signOut()
        if (ctx != null) {
            ImmichAuthHeaderInstaller.restorePlayerDataSource(ctx)
            ImmichBrowseInstaller.refresh(ctx)
            try {
                BrowsePresenter.instance(ctx).updateSections()
            } catch (_: Throwable) {
                // BrowsePresenter may not be ready yet
            }
        }
    }

    companion object {
        private const val TITLE_CHANGE_CREDENTIALS = "Change credentials"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sInstance: ImmichSettingsPresenter? = null

        fun instance(context: Context): ImmichSettingsPresenter {
            var current = sInstance
            if (current == null) {
                current = ImmichSettingsPresenter(context.applicationContext)
                sInstance = current
            }
            current.setContext(context)
            return current
        }

        private fun hostLabel(serverUrl: String?): String? {
            if (serverUrl.isNullOrBlank()) return null
            return try {
                val uri = Uri.parse(serverUrl.trim())
                uri.host ?: serverUrl.trim()
            } catch (_: Throwable) {
                serverUrl.trim()
            }
        }
    }
}
