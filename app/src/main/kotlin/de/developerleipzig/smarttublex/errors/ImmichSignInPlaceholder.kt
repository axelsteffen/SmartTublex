package de.developerleipzig.smarttublex.errors

import android.content.Context
import android.util.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import de.developerleipzig.immichapi.prefs.ImmichPrefs
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import de.developerleipzig.smarttublex.presenters.ImmichSignInPresenter

/**
 * Immich sidebar error / placeholder content.
 *
 * Copy is hardcoded (app resources are not merged into the wrapped APK).
 */
class ImmichSignInPlaceholder(
    context: Context,
    private val mode: Mode = Mode.SIGN_IN
) : ErrorFragmentData {
    private val appContext = context.applicationContext

    enum class Mode {
        DISABLED,
        SIGN_IN,
        /** URL + API key validated; browse rows arrive in Phase 3d. */
        CONNECTED
    }

    override fun onAction() {
        when (mode) {
            Mode.DISABLED -> {
                Log.i(SmartTublexApplication.TAG, "ImmichSignInPlaceholder: Immich disabled")
            }
            Mode.CONNECTED -> {
                Log.i(SmartTublexApplication.TAG, "ImmichSignInPlaceholder: already connected")
            }
            Mode.SIGN_IN -> {
                Log.i(SmartTublexApplication.TAG, "ImmichSignInPlaceholder: starting URL + API key sign-in")
                ImmichSignInPresenter.instance(appContext).start()
            }
        }
    }

    override fun getMessage(): String {
        return when (mode) {
            Mode.DISABLED -> "Immich is disabled"
            Mode.CONNECTED -> {
                val name = try {
                    ImmichPrefs.instance(appContext).userName
                } catch (_: Throwable) {
                    null
                }
                if (!name.isNullOrEmpty()) {
                    "Connected as $name"
                } else {
                    "Immich connected"
                }
            }
            Mode.SIGN_IN -> {
                if (SidebarSectionRegistry.isImmichReady(appContext)) {
                    "Connected to Immich"
                } else {
                    "Sign in to Immich with server URL and API key"
                }
            }
        }
    }

    override fun getActionText(): String? {
        return when (mode) {
            Mode.DISABLED, Mode.CONNECTED -> null
            Mode.SIGN_IN -> "Sign in"
        }
    }
}
