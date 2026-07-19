package de.developerleipzig.smarttublex.misc

import android.content.Context
import android.net.Uri
import android.util.Log
import com.liskovsoft.sharedutils.okhttp.OkHttpManager
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData
import de.developerleipzig.immichapi.network.ImmichUrlHelper
import de.developerleipzig.immichapi.prefs.ImmichPrefs
import de.developerleipzig.smarttublex.SmartTublexApplication
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Attaches Immich `x-api-key` to ExoPlayer media requests.
 *
 * Strategy (wrapper-only): host-scoped OkHttp interceptor + force OkHttp player data source
 * + skip profile-level check (odd H.264 profiles) while Immich is signed in.
 * Does not force software decode — SW H.264 on TVs causes stuttering / slow-motion.
 */
object ImmichAuthHeaderInstaller {
    private const val HEADER_API_KEY = "x-api-key"

    @Volatile
    private var interceptorInstalled = false

    @Volatile
    private var savedDataSource: Int? = null

    @Volatile
    private var savedSkipProfileLevel: Boolean? = null

    /** Cleared once after OkHttp is forced so a stale Cronet/Default factory is rebuilt. */
    @Volatile
    private var pendingFactoryClear = false

    /** One-shot undo of SW-decoder prefs left by older Immich builds. */
    @Volatile
    private var staleSwDecoderCleared = false

    fun ensureReady(context: Context) {
        if (!MediaSourceRegistry.isImmichEnabled()) return
        val prefs = ImmichPrefs.instance(context.applicationContext)
        val apiKey = prefs.apiKey
        val serverUrl = prefs.serverUrl
        if (apiKey.isNullOrEmpty() || serverUrl.isNullOrEmpty()) return

        val app = context.applicationContext
        installInterceptor()
        forceOkHttpDataSource(app)
        clearStaleSwDecoderForce(app)
        forceSkipProfileLevelForImmich(app)
        // Playback URLs already carry ?apiKey=. Do not release ExoMediaSourceFactory here:
        // ensureReady runs inside onNewVideo; clearing mid-prepare → Unexpected playback error null.
        pendingFactoryClear = false
    }

    /** Restore the user's previous player data source / profile-skip after Immich sign-out. */
    fun restorePlayerDataSource(context: Context) {
        val app = context.applicationContext
        try {
            val tweaks = PlayerTweaksData.instance(app)
            savedDataSource?.let {
                tweaks.setPlayerDataSource(it)
                Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: restored player data source=$it")
            }
            savedSkipProfileLevel?.let {
                tweaks.setProfileLevelCheckSkipped(it)
                Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: restored skipProfileLevel=$it")
            }
            savedDataSource = null
            savedSkipProfileLevel = null
            clearExoMediaSourceCache(app)
            pendingFactoryClear = false
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: restore player prefs failed", t)
        }
    }

    /** @return true if the interceptor was newly installed */
    private fun installInterceptor(): Boolean {
        if (interceptorInstalled) return false
        synchronized(this) {
            if (interceptorInstalled) return false
            try {
                val manager = OkHttpManager.instance()
                val clientField = OkHttpManager::class.java.getDeclaredField("mClient")
                clientField.isAccessible = true
                val existing = manager.client
                val rebuilt: OkHttpClient = existing.newBuilder()
                    .addInterceptor(ImmichApiKeyInterceptor)
                    .build()
                clientField.set(manager, rebuilt)
                interceptorInstalled = true
                Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: OkHttp interceptor installed")
                return true
            } catch (t: Throwable) {
                Log.e(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: interceptor install failed", t)
                return false
            }
        }
    }

    /** @return true if the player data source preference was changed */
    private fun forceOkHttpDataSource(context: Context): Boolean {
        try {
            val tweaks = PlayerTweaksData.instance(context)
            val current = tweaks.playerDataSource
            if (current == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP) {
                return false
            }
            if (savedDataSource == null) {
                savedDataSource = current
            }
            tweaks.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP)
            Log.i(
                SmartTublexApplication.TAG,
                "ImmichAuthHeaderInstaller: forced OkHttp data source (was $current)"
            )
            return true
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: force OkHttp failed", t)
            return false
        }
    }

    /**
     * Older Immich builds forced SW decode (TV slow-mo). Clear once per process.
     * Does not touch profile-level skip — Immich still enables that for HW edge cases.
     */
    private fun clearStaleSwDecoderForce(context: Context) {
        if (staleSwDecoderCleared) return
        staleSwDecoderCleared = true
        try {
            val tweaks = PlayerTweaksData.instance(context)
            if (!tweaks.isSWDecoderForced) {
                return
            }
            tweaks.setSWDecoderForced(false)
            Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: cleared stuck SW decoder force")
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: clear SW decoder failed", t)
        }
    }

    /** Skip Exo profile/level gating so odd but HW-decodable H.264 can play. */
    private fun forceSkipProfileLevelForImmich(context: Context) {
        try {
            val tweaks = PlayerTweaksData.instance(context)
            if (tweaks.isProfileLevelCheckSkipped) {
                return
            }
            if (savedSkipProfileLevel == null) {
                savedSkipProfileLevel = false
            }
            tweaks.setProfileLevelCheckSkipped(true)
            Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: skip profile-level check")
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: skip profile-level failed", t)
        }
    }

    /**
     * Clears cached HttpDataSource factory on the live [ExoPlayerController] so the next
     * open rebuilds with OkHttp + interceptor.
     * @return true if a factory was released
     */
    private fun clearExoMediaSourceCache(context: Context): Boolean {
        return try {
            val view = PlaybackPresenter.instance(context).view ?: return false
            val exoField = view.javaClass.getDeclaredField("mExoPlayerController")
            exoField.isAccessible = true
            val controller = exoField.get(view) ?: return false
            val factoryField = controller.javaClass.getDeclaredField("mMediaSourceFactory")
            factoryField.isAccessible = true
            val factory = factoryField.get(controller) ?: return false
            factory.javaClass.getMethod("release").invoke(factory)
            Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: ExoMediaSourceFactory cache cleared")
            true
        } catch (t: Throwable) {
            // Player not open yet — next ExoMediaSourceFactory will pick OkHttp.
            Log.d(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: no live factory to clear (${t.message})")
            false
        }
    }

    private object ImmichApiKeyInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val prefs = try {
                ImmichPrefs.instance()
            } catch (_: Throwable) {
                null
            }
            val apiKey = prefs?.apiKey
            val serverUrl = prefs?.serverUrl
            if (apiKey.isNullOrEmpty() || serverUrl.isNullOrEmpty()) {
                return chain.proceed(request)
            }
            if (!urlMatchesImmichServer(request.url().toString(), serverUrl)) {
                return chain.proceed(request)
            }
            if (!request.header(HEADER_API_KEY).isNullOrEmpty()) {
                return chain.proceed(request)
            }
            return chain.proceed(
                request.newBuilder()
                    .header(HEADER_API_KEY, apiKey)
                    .build()
            )
        }

        private fun urlMatchesImmichServer(requestUrl: String, serverUrl: String): Boolean {
            return try {
                val apiBase = ImmichUrlHelper.normalizeApiBaseUrl(serverUrl)
                val apiUri = Uri.parse(apiBase)
                val reqUri = Uri.parse(requestUrl)
                val apiHost = apiUri.host ?: return requestUrl.startsWith(apiBase)
                val reqHost = reqUri.host ?: return false
                if (!reqHost.equals(apiHost, ignoreCase = true)) return false
                // Same host: Immich media lives under /api/assets/…
                val path = reqUri.path ?: ""
                path.contains("/api/") || requestUrl.startsWith(apiBase)
            } catch (_: Throwable) {
                false
            }
        }
    }
}
