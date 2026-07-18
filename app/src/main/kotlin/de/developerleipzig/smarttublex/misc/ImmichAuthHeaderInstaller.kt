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
 * while Immich is signed in, then clear ExoMediaSourceFactory cache so the next open rebuilds.
 */
object ImmichAuthHeaderInstaller {
    private const val HEADER_API_KEY = "x-api-key"

    @Volatile
    private var interceptorInstalled = false

    @Volatile
    private var savedDataSource: Int? = null

    fun ensureReady(context: Context) {
        if (!MediaSourceRegistry.isImmichEnabled()) return
        val prefs = ImmichPrefs.instance(context.applicationContext)
        val apiKey = prefs.apiKey
        val serverUrl = prefs.serverUrl
        if (apiKey.isNullOrEmpty() || serverUrl.isNullOrEmpty()) return

        installInterceptor()
        forceOkHttpDataSource(context.applicationContext)
        clearExoMediaSourceCache(context.applicationContext)
    }

    /** Restore the user's previous player data source after Immich sign-out. */
    fun restorePlayerDataSource(context: Context) {
        val previous = savedDataSource ?: return
        savedDataSource = null
        try {
            PlayerTweaksData.instance(context.applicationContext).setPlayerDataSource(previous)
            clearExoMediaSourceCache(context.applicationContext)
            Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: restored player data source=$previous")
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: restore data source failed", t)
        }
    }

    private fun installInterceptor() {
        if (interceptorInstalled) return
        synchronized(this) {
            if (interceptorInstalled) return
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
            } catch (t: Throwable) {
                Log.e(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: interceptor install failed", t)
            }
        }
    }

    private fun forceOkHttpDataSource(context: Context) {
        try {
            val tweaks = PlayerTweaksData.instance(context)
            val current = tweaks.playerDataSource
            if (current == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP) {
                return
            }
            if (savedDataSource == null) {
                savedDataSource = current
            }
            tweaks.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP)
            Log.i(
                SmartTublexApplication.TAG,
                "ImmichAuthHeaderInstaller: forced OkHttp data source (was $current)"
            )
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: force OkHttp failed", t)
        }
    }

    /**
     * Clears cached HttpDataSource factory on the live [ExoPlayerController] so the next
     * open rebuilds with OkHttp + interceptor.
     */
    private fun clearExoMediaSourceCache(context: Context) {
        try {
            val view = PlaybackPresenter.instance(context).view ?: return
            val exoField = view.javaClass.getDeclaredField("mExoPlayerController")
            exoField.isAccessible = true
            val controller = exoField.get(view) ?: return
            val factoryField = controller.javaClass.getDeclaredField("mMediaSourceFactory")
            factoryField.isAccessible = true
            val factory = factoryField.get(controller) ?: return
            factory.javaClass.getMethod("release").invoke(factory)
            Log.i(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: ExoMediaSourceFactory cache cleared")
        } catch (t: Throwable) {
            // Player not open yet — next ExoMediaSourceFactory will pick OkHttp.
            Log.d(SmartTublexApplication.TAG, "ImmichAuthHeaderInstaller: no live factory to clear (${t.message})")
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
