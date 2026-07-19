package de.developerleipzig.smarttublex.misc

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager
import de.developerleipzig.smarttublex.SmartTublexApplication
import de.developerleipzig.smarttublex.presenters.ImmichSettingsPresenter
import de.developerleipzig.smarttublex.presenters.PlexSettingsPresenter
import java.util.concurrent.Callable

/**
 * Single owner of [BrowsePresenter] `mSettingsGridMapping` for wrapper extras
 * (Plex + Immich). Avoids last-write-wins when both installers run.
 */
object SettingsGridInstaller {
    @Volatile
    private var lastInjectedPresenter: BrowsePresenter? = null

    fun ensureInstalled(context: Context) {
        if (!MediaSourceRegistry.isPlexEnabled() && !MediaSourceRegistry.isImmichEnabled()) {
            return
        }
        val appContext = context.applicationContext
        val presenter = try {
            BrowsePresenter.instance(appContext)
        } catch (t: Throwable) {
            Log.w(SmartTublexApplication.TAG, "SettingsGridInstaller: BrowsePresenter not ready", t)
            return
        }
        if (lastInjectedPresenter === presenter) {
            return
        }
        if (!injectSettingsMapping(presenter)) {
            return
        }
        lastInjectedPresenter = presenter
        Log.i(SmartTublexApplication.TAG, "SettingsGridInstaller: settings grid mapping injected")
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
                "SettingsGridInstaller: failed to inject mSettingsGridMapping",
                t
            )
            false
        }
    }

    private fun buildSettingsItems(presenter: BrowsePresenter): List<SettingsItem> {
        val ctx = presenter.context ?: return emptyList()
        val items = ArrayList(AppDataSourceManager.instance().getSettingItems(ctx))

        val accountsTitle = try {
            ctx.getString(R.string.settings_accounts)
        } catch (_: Throwable) {
            null
        }
        var insertAt = if (accountsTitle != null) {
            val accountsIndex = items.indexOfFirst { it.title == accountsTitle }
            if (accountsIndex >= 0) accountsIndex + 1 else 0
        } else {
            0
        }

        if (MediaSourceRegistry.isPlexEnabled()
            && items.none { it.title == SidebarSectionRegistry.TITLE_SETTINGS_PLEX }
        ) {
            items.add(
                insertAt,
                SettingsItem(
                    SidebarSectionRegistry.TITLE_SETTINGS_PLEX,
                    { PlexSettingsPresenter.instance(ctx).show() },
                    R.drawable.icon_playlist
                )
            )
            insertAt++
        }

        if (MediaSourceRegistry.isImmichEnabled()
            && items.none { it.title == SidebarSectionRegistry.TITLE_SETTINGS_IMMICH }
        ) {
            items.add(
                insertAt,
                SettingsItem(
                    SidebarSectionRegistry.TITLE_SETTINGS_IMMICH,
                    { ImmichSettingsPresenter.instance(ctx).show() },
                    R.drawable.icon_playlist
                )
            )
        }

        return items
    }
}
