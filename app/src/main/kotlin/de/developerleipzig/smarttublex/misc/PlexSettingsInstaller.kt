package de.developerleipzig.smarttublex.misc

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.presenters.PlexSettingsPresenter
import java.util.concurrent.Callable

/**
 * Injects a Plex [SettingsItem] into upstream settings grid
 * ([BrowsePresenter] `mSettingsGridMapping`), matching SmartTube's
 * [AppDataSourceManager] Plex entry.
 */
object PlexSettingsInstaller {
    @Volatile
    private var lastInjectedPresenter: BrowsePresenter? = null

    fun ensureInstalled(context: Context) {
        if (!MediaSourceRegistry.isPlexEnabled()) {
            return
        }
        val appContext = context.applicationContext
        val presenter = try {
            BrowsePresenter.instance(appContext)
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "PlexSettingsInstaller: BrowsePresenter not ready", t)
            return
        }
        if (lastInjectedPresenter === presenter) {
            return
        }
        if (!injectSettingsMapping(presenter)) {
            return
        }
        lastInjectedPresenter = presenter
        Log.i(SmartTublexApplication.TAG, "PlexSettingsInstaller: Plex settings item injected")
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectSettingsMapping(presenter: BrowsePresenter): Boolean {
        return try {
            val field = BrowsePresenter::class.java.getDeclaredField("mSettingsGridMapping")
            field.isAccessible = true
            val mapping = field.get(presenter) as MutableMap<Int, Callable<List<SettingsItem>>>
            mapping[MediaGroup.TYPE_SETTINGS] = Callable {
                buildSettingsItems(presenter)
            }
            true
        } catch (t: Throwable) {
            Log.e(
                SmartTublexApplication.TAG,
                "PlexSettingsInstaller: failed to inject mSettingsGridMapping",
                t
            )
            false
        }
    }

    private fun buildSettingsItems(presenter: BrowsePresenter): List<SettingsItem> {
        val ctx = presenter.context ?: return emptyList()
        val items = ArrayList(AppDataSourceManager.instance().getSettingItems(ctx))
        if (items.any { it.title == SidebarSectionRegistry.TITLE_PLEX }) {
            return items
        }
        val plexItem = SettingsItem(
            SidebarSectionRegistry.TITLE_PLEX,
            { PlexSettingsPresenter.instance(ctx).show() },
            R.drawable.icon_playlist
        )
        val accountsTitle = try {
            ctx.getString(R.string.settings_accounts)
        } catch (_: Throwable) {
            null
        }
        val accountsIndex = if (accountsTitle != null) {
            items.indexOfFirst { it.title == accountsTitle }
        } else {
            -1
        }
        if (accountsIndex >= 0) {
            items.add(accountsIndex + 1, plexItem)
        } else {
            items.add(0, plexItem)
        }
        return items
    }
}
