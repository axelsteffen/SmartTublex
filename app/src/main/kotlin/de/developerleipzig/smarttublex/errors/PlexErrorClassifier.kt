package de.developerleipzig.smarttublex.errors

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/**
 * Classifies Plex network / auth failures for clear user messaging (Phase 4.6).
 * Hardcoded copy — app resources are not merged into the wrapped APK.
 */
object PlexErrorClassifier {
    enum class Kind {
        AUTH,
        OFFLINE,
        GENERIC
    }

    fun classify(error: Throwable?): Kind {
        var t = error
        while (t != null) {
            when (t) {
                is UnknownHostException,
                is ConnectException,
                is SocketTimeoutException,
                is NoRouteToHostException -> return Kind.OFFLINE
            }
            if (t.javaClass.simpleName == "HttpException") {
                val code = httpStatusCode(t)
                if (code == 401 || code == 403) return Kind.AUTH
            }
            val msg = t.message
            if (!msg.isNullOrEmpty()) {
                val lower = msg.lowercase(Locale.US)
                if (containsAny(
                        msg,
                        "HTTP 401", "HTTP 403", "Response code: 401", "Response code: 403",
                        "Unauthorized", "401 Unauthorized", "403 Forbidden"
                    ) || lower.contains("unauthorized")
                ) {
                    return Kind.AUTH
                }
                if (containsAny(
                        lower,
                        "unable to resolve host",
                        "failed to connect",
                        "connection refused",
                        "network is unreachable",
                        "no address associated",
                        "software caused connection abort",
                        "timed out",
                        "timeout"
                    )
                ) {
                    return Kind.OFFLINE
                }
            }
            t = t.cause
        }
        return Kind.GENERIC
    }

    /** User-facing browse message (library load). */
    fun browseMessage(error: Throwable?): String {
        return browseMessage(classify(error))
    }

    fun browseMessage(kind: Kind): String {
        val german = isGerman()
        return when (kind) {
            Kind.AUTH -> if (german) {
                "Plex-Anmeldung abgelaufen — bitte erneut anmelden"
            } else {
                "Plex sign-in expired — please sign in again"
            }
            Kind.OFFLINE -> if (german) {
                "Plex-Server ist nicht erreichbar.\nPrüfen Sie, ob der Server läuft, und versuchen Sie es erneut."
            } else {
                "Plex server is offline or unreachable.\nCheck that the server is running, then try again."
            }
            Kind.GENERIC -> if (german) {
                "Plex-Bibliothek konnte nicht geladen werden"
            } else {
                "Unable to load Plex library"
            }
        }
    }

    /** User-facing playback toast. */
    fun playbackMessage(error: Throwable?): String {
        val german = isGerman()
        return when (classify(error)) {
            Kind.AUTH -> if (german) {
                "Plex-Anmeldung abgelaufen — bitte erneut anmelden"
            } else {
                "Plex sign-in expired — please sign in again"
            }
            Kind.OFFLINE -> if (german) {
                "Wiedergabe fehlgeschlagen: Plex-Server nicht erreichbar"
            } else {
                "Playback failed: Plex server unreachable"
            }
            Kind.GENERIC -> if (german) {
                "Dieses Plex-Video konnte nicht abgespielt werden"
            } else {
                "Unable to play this Plex video"
            }
        }
    }

    fun retryActionText(): String =
        if (isGerman()) "Erneut versuchen" else "Try again"

    fun selectServerActionText(): String =
        if (isGerman()) "Server wählen" else "Select server"

    fun signInActionText(): String =
        if (isGerman()) "Anmelden" else "Sign in"

    private fun isGerman(): Boolean =
        Locale.getDefault().language.equals("de", ignoreCase = true)

    private fun containsAny(haystack: String, vararg needles: String): Boolean {
        for (needle in needles) {
            if (haystack.contains(needle, ignoreCase = true)) return true
        }
        return false
    }

    private fun httpStatusCode(t: Throwable): Int {
        try {
            val code = t.javaClass.getMethod("code").invoke(t)
            if (code is Int) return code
        } catch (_: Throwable) {
            // fall through
        }
        val msg = t.message ?: return -1
        val idx = msg.indexOf("HTTP ")
        if (idx >= 0 && msg.length >= idx + 8) {
            try {
                return msg.substring(idx + 5, idx + 8).trim().toInt()
            } catch (_: NumberFormatException) {
                // ignore
            }
        }
        return -1
    }
}
