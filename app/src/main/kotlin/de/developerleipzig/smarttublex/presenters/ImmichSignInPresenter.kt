package de.developerleipzig.smarttublex.presenters

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import de.developerleipzig.immichapi.ImmichServiceManager
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.browse.ImmichBrowseInstaller
import de.developerleipzig.smarttublex.misc.MediaSourceRegistry
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import io.reactivex.disposables.Disposable

/**
 * Immich sign-in: server URL + API key via [SimpleEditDialog], then validate against `/api/users/me`.
 *
 * Copy is hardcoded — app resources are not merged into the wrapped APK.
 */
class ImmichSignInPresenter private constructor(context: Context) : BasePresenter<Void>(context) {
    private var validateAction: Disposable? = null

    fun unhold() {
        RxHelper.disposeActions(validateAction)
        sInstance = null
    }

    fun start() {
        val ctx = context ?: return
        if (!MediaSourceRegistry.isImmichEnabled()) {
            MessageHelpers.showMessage(ctx, "Immich integration is not available")
            return
        }
        ImmichServiceManager.init(ctx)
        promptServerUrl()
    }

    private fun promptServerUrl() {
        val ctx = context ?: return
        val signIn = MediaSourceRegistry.getImmichServiceManager().signInService
        val existing = signIn.serverUrl ?: ""
        SimpleEditDialog.show(
            ctx,
            "Immich server URL",
            "https://immich.example",
            existing,
            { value ->
                val trimmed = value.trim()
                if (trimmed.isEmpty()) {
                    return@show false
                }
                signIn.serverUrl = trimmed
                promptApiKey()
                true
            }
        )
    }

    private fun promptApiKey() {
        val ctx = context ?: return
        val signIn = MediaSourceRegistry.getImmichServiceManager().signInService
        val existing = signIn.apiKey ?: ""
        SimpleEditDialog.showPassword(
            ctx,
            "Immich API key",
            existing,
            { value ->
                val trimmed = value.trim()
                if (trimmed.isEmpty()) {
                    return@showPassword false
                }
                signIn.apiKey = trimmed
                validateCredentials()
                true
            }
        )
    }

    private fun validateCredentials() {
        val ctx = context ?: return
        LoadingManager.showLoading(ctx, true)
        RxHelper.disposeActions(validateAction)
        val signIn = MediaSourceRegistry.getImmichServiceManager().signInService
        validateAction = signIn.validateObserve()
            .subscribe(
                { user ->
                    LoadingManager.showLoading(ctx, false)
                    val name = user?.name ?: user?.email ?: "Immich"
                    Log.i(SmartTublexApplication.TAG, "Immich validated as $name")
                    MessageHelpers.showMessage(ctx, "Connected as $name")
                    ImmichBrowseInstaller.refresh(ctx)
                    try {
                        val browse = BrowsePresenter.instance(ctx)
                        browse.selectSection(SidebarSectionRegistry.TYPE_IMMICH)
                        browse.refresh()
                    } catch (t: Throwable) {
                        Log.w(SmartTublexApplication.TAG, "ImmichSignInPresenter: browse refresh failed", t)
                    }
                    unhold()
                },
                { error ->
                    LoadingManager.showLoading(ctx, false)
                    val detail = formatError(error)
                    Log.e(SmartTublexApplication.TAG, "Immich validate failed: $detail", error)
                    MessageHelpers.showMessage(ctx, detail)
                    // Keep credentials; user can retry from Sign in
                    ImmichBrowseInstaller.refresh(ctx)
                }
            )
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sInstance: ImmichSignInPresenter? = null

        fun instance(context: Context): ImmichSignInPresenter {
            var current = sInstance
            if (current == null) {
                current = ImmichSignInPresenter(context.applicationContext)
                sInstance = current
            }
            current.setContext(context)
            return current
        }

        private fun formatError(error: Throwable?): String {
            if (error == null) return "Immich sign-in failed"
            val message = error.message
            if (!message.isNullOrEmpty()) return message
            val cause = error.cause
            if (cause?.message?.isNotEmpty() == true) {
                return "${cause.javaClass.simpleName}: ${cause.message}"
            }
            return error.javaClass.simpleName
        }
    }
}
