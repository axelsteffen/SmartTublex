package de.developerleipzig.smarttublex.browse;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Java façade over {@link SidebarService}.
 *
 * <p>Kotlin cannot reference {@code SidebarService} from the apk2maven fat JAR: that class
 * implements nested {@code AppPrefs.ProfileChangeListener}, and dex2jar drops the
 * {@code InnerClasses} attribute on the implementor, which triggers
 * {@code Cannot access ...ProfileChangeListener which is a supertype of ...SidebarService}.
 * javac does not apply that check, so this bridge keeps the Kotlin call sites on {@link Object}.
 */
public final class SidebarServiceBridge {
    private SidebarServiceBridge() {}

    public static Object instance(Context context) {
        return SidebarService.instance(context);
    }

    public static boolean isSectionPinned(Object sidebar, int sectionId) {
        return ((SidebarService) sidebar).isSectionPinned(sectionId);
    }

    public static void persistState(Object sidebar) {
        ((SidebarService) sidebar).persistState();
    }

    @SuppressWarnings("unchecked")
    public static List<Video> pinnedItems(Object sidebar) throws ReflectiveOperationException {
        Field field = SidebarService.class.getDeclaredField("mPinnedItems");
        field.setAccessible(true);
        return (List<Video>) field.get(sidebar);
    }
}
