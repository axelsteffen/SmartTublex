package de.developerleipzig.immichapi.adapter;

import androidx.annotation.Nullable;

import de.developerleipzig.immichapi.library.ImmichPage;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAlbum;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Fork-only adapter: wraps Immich album / asset pages as MSC {@link MediaGroup}.
 */
public final class ImmichMediaGroupAdapter implements MediaGroup {
    public enum Kind {
        ALBUM_ROW,
        ALBUM_GRID,
        RECENT_VIDEOS,
        YEAR_ROW,
        YEAR_GRID,
        ALBUMS_LIST
    }

    private final Kind mKind;
    private final ImmichAlbum mAlbum;
    private final int mYear;
    private final String mTitle;
    private final List<MediaItem> mMediaItems;
    private final String mNextPageKey;

    private ImmichMediaGroupAdapter(Kind kind,
                                    @Nullable ImmichAlbum album,
                                    int year,
                                    @Nullable String title,
                                    List<MediaItem> mediaItems,
                                    @Nullable String nextPageKey) {
        mKind = kind != null ? kind : Kind.ALBUM_ROW;
        mAlbum = album;
        mYear = year;
        mTitle = title;
        mMediaItems = mediaItems;
        mNextPageKey = nextPageKey;
    }

    /**
     * Home row for one album: browse stub + preview assets.
     */
    @Nullable
    public static ImmichMediaGroupAdapter fromAlbum(@Nullable ImmichAlbum album,
                                                    @Nullable List<ImmichAsset> items,
                                                    @Nullable ImmichPage page) {
        if (album == null || album.getId() == null || album.getId().isEmpty()) {
            return null;
        }
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        MediaItem browseStub = ImmichMediaItemAdapter.fromAlbumBrowse(album);
        if (browseStub != null) {
            mediaItems.add(browseStub);
        }
        appendAssets(mediaItems, items);
        List<MediaItem> result = mediaItems.isEmpty() ? null : mediaItems;
        return new ImmichMediaGroupAdapter(
                Kind.ALBUM_ROW, album, -1, null, result, nextPageKeyFrom(page));
    }

    /**
     * Full album grid (no browse stub).
     */
    @Nullable
    public static ImmichMediaGroupAdapter fromAlbumGrid(@Nullable ImmichAlbum album,
                                                        @Nullable List<ImmichAsset> items,
                                                        @Nullable ImmichPage page) {
        if (album == null || album.getId() == null || album.getId().isEmpty()) {
            return null;
        }
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        appendAssets(mediaItems, items);
        List<MediaItem> result = mediaItems.isEmpty() ? null : mediaItems;
        return new ImmichMediaGroupAdapter(
                Kind.ALBUM_GRID, album, -1, album.getTitle(), result, nextPageKeyFrom(page));
    }

    /**
     * Single row listing album cards only (no nested asset previews).
     */
    @Nullable
    public static ImmichMediaGroupAdapter fromAlbumsList(@Nullable List<ImmichAlbum> albums) {
        if (albums == null || albums.isEmpty()) {
            return null;
        }
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        for (ImmichAlbum album : albums) {
            MediaItem stub = ImmichMediaItemAdapter.fromAlbumBrowse(album);
            if (stub != null) {
                mediaItems.add(stub);
            }
        }
        if (mediaItems.isEmpty()) {
            return null;
        }
        return new ImmichMediaGroupAdapter(
                Kind.ALBUMS_LIST, null, -1, "Alben", mediaItems, null);
    }

    /**
     * Assets for one calendar year (Fotos section).
     */
    @Nullable
    public static ImmichMediaGroupAdapter fromYear(@Nullable Integer year,
                                                   @Nullable List<ImmichAsset> items,
                                                   @Nullable ImmichPage page) {
        if (year == null || year <= 0) {
            return null;
        }
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        appendAssets(mediaItems, items);
        if (mediaItems.isEmpty()) {
            return null;
        }
        return new ImmichMediaGroupAdapter(
                Kind.YEAR_ROW, null, year, String.valueOf(year), mediaItems, nextPageKeyFrom(page));
    }

    @Nullable
    public static ImmichMediaGroupAdapter fromYearGrid(int year,
                                                       @Nullable List<ImmichAsset> items,
                                                       @Nullable ImmichPage page) {
        if (year <= 0) {
            return null;
        }
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        appendAssets(mediaItems, items);
        List<MediaItem> result = mediaItems.isEmpty() ? null : mediaItems;
        return new ImmichMediaGroupAdapter(
                Kind.YEAR_GRID, null, year, String.valueOf(year), result, nextPageKeyFrom(page));
    }

    /**
     * Recent videos shelf.
     */
    @Nullable
    public static ImmichMediaGroupAdapter fromRecentVideos(@Nullable List<ImmichAsset> items,
                                                           @Nullable ImmichPage page) {
        return fromRecentVideos("Recent Videos", items, page);
    }

    @Nullable
    public static ImmichMediaGroupAdapter fromRecentVideos(@Nullable String title,
                                                           @Nullable List<ImmichAsset> items,
                                                           @Nullable ImmichPage page) {
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        appendAssets(mediaItems, items);
        if (mediaItems.isEmpty()) {
            return null;
        }
        return new ImmichMediaGroupAdapter(
                Kind.RECENT_VIDEOS, null, -1, title, mediaItems, nextPageKeyFrom(page));
    }

    /**
     * Continuation page: new items only.
     */
    @Nullable
    public static ImmichMediaGroupAdapter continueFrom(@Nullable ImmichMediaGroupAdapter base,
                                                       @Nullable List<ImmichAsset> items,
                                                       @Nullable ImmichPage page) {
        if (base == null) {
            return null;
        }
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        appendAssets(mediaItems, items);
        if (mediaItems.isEmpty()) {
            return null;
        }
        return new ImmichMediaGroupAdapter(
                base.mKind, base.mAlbum, base.mYear, base.mTitle, mediaItems, nextPageKeyFrom(page));
    }

    public Kind getKind() {
        return mKind;
    }

    public int getYear() {
        return mYear;
    }

    @Nullable
    public ImmichAlbum getImmichAlbum() {
        return mAlbum;
    }

    @Override
    public int getType() {
        return TYPE_MOVIES;
    }

    @Nullable
    @Override
    public List<MediaItem> getMediaItems() {
        return mMediaItems;
    }

    @Override
    public String getTitle() {
        if (mTitle != null && !mTitle.isEmpty()) {
            return mTitle;
        }
        return mAlbum != null ? mAlbum.getTitle() : null;
    }

    @Override
    public String getChannelId() {
        return null;
    }

    @Override
    public String getParams() {
        if (mAlbum != null) {
            return mAlbum.getId();
        }
        return mYear > 0 ? String.valueOf(mYear) : null;
    }

    @Override
    public String getReloadPageKey() {
        return null;
    }

    @Override
    public String getNextPageKey() {
        return mNextPageKey;
    }

    @Override
    public String getChannelUrl() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return mMediaItems == null || mMediaItems.isEmpty();
    }

    @Nullable
    private static String nextPageKeyFrom(@Nullable ImmichPage page) {
        if (page == null) {
            return null;
        }
        int nextOffset = page.getNextOffset();
        return nextOffset >= 0 ? String.valueOf(nextOffset) : null;
    }

    private static void appendAssets(ArrayList<MediaItem> mediaItems,
                                     @Nullable List<ImmichAsset> items) {
        if (items == null) {
            return;
        }
        for (ImmichAsset asset : items) {
            MediaItem adapted = ImmichMediaItemAdapter.from(asset);
            if (adapted != null) {
                mediaItems.add(adapted);
            }
        }
    }
}
