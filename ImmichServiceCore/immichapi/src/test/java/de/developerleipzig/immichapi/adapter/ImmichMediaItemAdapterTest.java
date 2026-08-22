package de.developerleipzig.immichapi.adapter;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import de.developerleipzig.immichapi.library.ImmichAlbumImpl;
import de.developerleipzig.immichapi.library.ImmichAssetImpl;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ImmichMediaItemAdapterTest {
    private static ImmichAsset video(long durationMs) {
        return new ImmichAssetImpl("asset-1", "clip.mp4", ImmichAsset.TYPE_VIDEO, durationMs,
                "https://immich/thumb", "video/mp4");
    }

    @Test
    public void badgeText_videoUnderAnHour_omitsHours() {
        MediaItem item = ImmichMediaItemAdapter.from(video(754_000L));

        assertNotNull(item);
        assertEquals("12:34", item.getBadgeText());
    }

    @Test
    public void badgeText_longVideo_includesHours() {
        MediaItem item = ImmichMediaItemAdapter.from(video(5_400_000L));

        assertNotNull(item);
        assertEquals("1:30:00", item.getBadgeText());
    }

    @Test
    public void badgeText_videoWithoutDuration_isNull() {
        MediaItem item = ImmichMediaItemAdapter.from(video(0L));

        assertNotNull(item);
        assertNull(item.getBadgeText());
    }

    @Test
    public void badgeText_stillImage_isNull() {
        MediaItem item = ImmichMediaItemAdapter.from(new ImmichAssetImpl(
                "asset-2", "photo.jpg", ImmichAsset.TYPE_IMAGE, 0L,
                "https://immich/thumb", "image/jpeg"));

        assertNotNull(item);
        assertNull(item.getBadgeText());
    }

    @Test
    public void badgeText_album_countsAssets() {
        MediaItem item = ImmichMediaItemAdapter.fromAlbumBrowse(
                new ImmichAlbumImpl("alb-1", "Vacation", 24, "asset-1", "https://immich/thumb"));

        assertNotNull(item);
        assertEquals("24 Medien", item.getBadgeText());
    }

    @Test
    public void badgeText_albumWithSingleAsset_isSingular() {
        MediaItem item = ImmichMediaItemAdapter.fromAlbumBrowse(
                new ImmichAlbumImpl("alb-2", "Kids", 1, "asset-1", "https://immich/thumb"));

        assertNotNull(item);
        assertEquals("1 Medium", item.getBadgeText());
    }

    @Test
    public void badgeText_emptyAlbum_isNull() {
        MediaItem item = ImmichMediaItemAdapter.fromAlbumBrowse(
                new ImmichAlbumImpl("alb-3", "Empty", 0, null, null));

        assertNotNull(item);
        assertNull(item.getBadgeText());
    }
}
