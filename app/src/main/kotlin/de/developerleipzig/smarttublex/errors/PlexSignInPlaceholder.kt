package de.developerleipzig.smarttublex.errors

import android.content.Context
import android.util.Log
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.browse.PlexBrowseInstaller
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import de.developerleipzig.smarttublex.presenters.PlexServerSelectionPresenter
import de.developerleipzig.smarttublex.presenters.PlexSignInPresenter

/**
 * Plex sidebar error / placeholder content.
 *
 * Modes cover sign-in, server offline, and generic load failure.
 * Copy is hardcoded (app resources are not merged into the wrapped APK).
 */
class PlexSignInPlaceholder(
    context: Context,
    private val mode: Mode = Mode.SIGN_IN
) : ErrorFragmentData {
    private val appContext = context.applicationContext

    enum class Mode {
        DISABLED,
        SIGN_IN,
        /** Auth + server OK; library rows arrive in Phase 3c. */
        CONNECTED,
        /** Selected PMS is down / unreachable. */
        SERVER_UNAVAILABLE,
        /** Auth token rejected by plex.tv or PMS. */
        AUTH_EXPIRED,
        /** Non-network library load failure. */
        LOAD_FAILED
    }

    override fun onAction() {
        when (mode) {
            Mode.DISABLED -> {
                Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: Plex disabled")
            }
            Mode.CONNECTED -> {
                Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: already connected")
            }
            Mode.SIGN_IN, Mode.AUTH_EXPIRED -> {
                PlexServiceManager.init(appContext)
                val signed = try {
                    PlexServiceManager.instance().signInService.isSigned
                } catch (_: Throwable) {
                    false
                }
                if (signed && mode == Mode.SIGN_IN) {
                    Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: signed in — server picker")
                    PlexServerSelectionPresenter.instance(appContext).show(true)
                } else {
                    Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: starting PIN sign-in")
                    PlexSignInPresenter.instance(appContext).start()
                }
            }
            Mode.SERVER_UNAVAILABLE, Mode.LOAD_FAILED -> {
                Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: retry browse ($mode)")
                retryBrowse()
            }
        }
    }

    override fun getMessage(): String {
        return when (mode) {
            Mode.DISABLED -> "Plex is disabled"
            Mode.CONNECTED -> {
                val server = try {
                    PlexServiceManager.instance().serverService.selectedServer?.name
                } catch (_: Throwable) {
                    null
                }
                if (server != null) {
                    "Connected to $server"
                } else {
                    "Plex connected"
                }
            }
            Mode.SIGN_IN -> {
                if (SidebarSectionRegistry.isPlexReady(appContext)) {
                    "Connected to Plex"
                } else {
                    "Sign in to Plex to browse your libraries"
                }
            }
            Mode.SERVER_UNAVAILABLE ->
                PlexErrorClassifier.browseMessage(PlexErrorClassifier.Kind.OFFLINE)
            Mode.AUTH_EXPIRED ->
                PlexErrorClassifier.browseMessage(PlexErrorClassifier.Kind.AUTH)
            Mode.LOAD_FAILED ->
                PlexErrorClassifier.browseMessage(PlexErrorClassifier.Kind.GENERIC)
        }
    }

    override fun getActionText(): String? {
        return when (mode) {
            Mode.DISABLED, Mode.CONNECTED -> null
            Mode.SIGN_IN -> {
                val signed = try {
                    PlexServiceManager.instance().signInService.isSigned
                } catch (_: Throwable) {
                    false
                }
                if (signed) {
                    PlexErrorClassifier.selectServerActionText()
                } else {
                    PlexErrorClassifier.signInActionText()
                }
            }
            Mode.AUTH_EXPIRED -> PlexErrorClassifier.signInActionText()
            Mode.SERVER_UNAVAILABLE, Mode.LOAD_FAILED -> PlexErrorClassifier.retryActionText()
        }
    }

    private fun retryBrowse() {
        PlexBrowseInstaller.refresh(appContext)
        try {
            val browse = BrowsePresenter.instance(appContext)
            browse.selectSection(SidebarSectionRegistry.TYPE_PLEX)
            browse.refresh()
        } catch (t: Throwable) {
            Log.e(SmartTublexApplication.TAG, "PlexSignInPlaceholder: retry failed", t)
        }
    }
}
