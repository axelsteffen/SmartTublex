package de.developerleipzig.smarttublex.presenters

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.plexserviceinterfaces.data.PlexAuthPin
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter
import de.developerleipzig.smarttublex.SmartTublexApplication
import io.reactivex.disposables.Disposable

/**
 * Phase 3b: Plex PIN auth reusing upstream [SignInView].
 *
 * Upstream [SignInPresenter.instance] only dispatches to YT/Google. We install this subclass
 * as the SignInPresenter singleton via reflection so SignInFragment drives our PIN flow.
 */
class PlexSignInPresenter private constructor(context: Context) : SignInPresenter(context) {
    private var signInAction: Disposable? = null

    override fun onViewDestroyed() {
        super.onViewDestroyed()
        unholdInstance()
    }

    override fun onViewInitialized() {
        // WRAPPER: subclass early-return in upstream SignInPresenter — run PIN flow ourselves
        super.onViewInitialized()
        RxHelper.disposeActions(signInAction)
        startPinFlow()
    }

    override fun onActionClicked() {
        getView()?.close()
    }

    override fun start() {
        installAsSignInSingleton(this)
        super.start()
        RxHelper.disposeActions(signInAction)
    }

    private fun startPinFlow() {
        val ctx = context
        if (ctx != null) {
            PlexServiceManager.init(ctx)
        }
        val signInService = PlexServiceManager.instance().signInService
        signInAction = signInService.signInWithPinObserve()
            .subscribe(
                { pin -> showPin(pin) },
                { error ->
                    val detail = formatSignInError(error)
                    Log.e(SmartTublexApplication.TAG, "Plex sign-in error: $detail", error)
                    getView()?.showCode(detail, SIGN_IN_URL)
                },
                {
                    val appCtx = context
                    getView()?.close()
                    if (appCtx != null) {
                        Log.i(SmartTublexApplication.TAG, "Plex PIN claimed — opening server picker")
                        PlexServerSelectionPresenter.instance(appCtx).show(true)
                    }
                }
            )
    }

    private fun showPin(pin: PlexAuthPin?) {
        val view = getView()
        if (view == null || pin == null || pin.code == null) {
            return
        }
        val authUrl = pin.authUrl ?: SIGN_IN_URL
        view.showCode(pin.code, authUrl, QR_SIGN_IN_URL_PREFIX + pin.code)
        Log.i(SmartTublexApplication.TAG, "Plex PIN shown: ${pin.code}")
    }

    companion object {
        private const val SIGN_IN_URL = "https://plex.tv/link"
        private const val QR_SIGN_IN_URL_PREFIX = "https://plex.tv/api/v2/pins/qr/"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sInstance: PlexSignInPresenter? = null

        fun instance(context: Context): PlexSignInPresenter {
            var current = sInstance
            if (current == null) {
                current = PlexSignInPresenter(context.applicationContext)
                sInstance = current
            }
            current.setContext(context)
            return current
        }

        fun unholdInstance() {
            sInstance?.let { RxHelper.disposeActions(it.signInAction) }
            sInstance = null
            clearUpstreamSingleton()
        }

        /** Point upstream SignInFragment at this presenter. */
        private fun installAsSignInSingleton(presenter: PlexSignInPresenter) {
            try {
                val field = SignInPresenter::class.java.getDeclaredField("sInstance")
                field.isAccessible = true
                field.set(null, presenter)
            } catch (t: Throwable) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "Failed to install PlexSignInPresenter as SignInPresenter.sInstance",
                    t
                )
            }
        }

        private fun clearUpstreamSingleton() {
            try {
                val field = SignInPresenter::class.java.getDeclaredField("sInstance")
                field.isAccessible = true
                val current = field.get(null)
                if (current is PlexSignInPresenter) {
                    field.set(null, null)
                }
            } catch (_: Throwable) {
                // ignore
            }
        }

        private fun formatSignInError(error: Throwable?): String {
            if (error == null) return "Unknown error"
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
