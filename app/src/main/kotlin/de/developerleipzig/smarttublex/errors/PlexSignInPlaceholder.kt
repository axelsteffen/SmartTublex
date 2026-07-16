package de.developerleipzig.smarttublex.errors

import android.content.Context
import android.util.Log
import com.liskovsoft.plexapi.PlexServiceManager
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.misc.SidebarSectionRegistry
import de.developerleipzig.smarttublex.presenters.PlexServerSelectionPresenter
import de.developerleipzig.smarttublex.presenters.PlexSignInPresenter

/**
 * Plex sidebar error / placeholder content.
 * Phase 3b: [onAction] starts PIN auth or server selection.
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
        CONNECTED
    }

    override fun onAction() {
        when (mode) {
            Mode.DISABLED -> {
                Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: Plex disabled")
            }
            Mode.CONNECTED -> {
                Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: already connected (3c rows pending)")
            }
            Mode.SIGN_IN -> {
                PlexServiceManager.init(appContext)
                val signed = PlexServiceManager.instance().signInService.isSigned
                if (signed) {
                    Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: signed in — server picker")
                    PlexServerSelectionPresenter.instance(appContext).show(true)
                } else {
                    Log.i(SmartTublexApplication.TAG, "PlexSignInPlaceholder: starting PIN sign-in")
                    PlexSignInPresenter.instance(appContext).start()
                }
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
                    "Connected to $server. Library browse comes next."
                } else {
                    "Plex connected. Library browse comes next."
                }
            }
            Mode.SIGN_IN -> {
                if (SidebarSectionRegistry.isPlexReady(appContext)) {
                    "Plex connected. Library browse comes next."
                } else {
                    "Sign in to Plex to browse your libraries"
                }
            }
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
                if (signed) "Select server" else "Sign in"
            }
        }
    }
}
