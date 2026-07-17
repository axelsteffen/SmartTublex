package de.developerleipzig.immichapi.adapter;

import android.media.Rating;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import de.developerleipzig.immichserviceinterfaces.data.ImmichAlbum;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;
import de.developerleipzig.immichserviceinterfaces.data.ImmichBackedMediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

/**
 * Fork-only adapter: wraps {@link ImmichAsset} (or album stub) as MSC {@link MediaItem}.
 * <p>
 * {@link #getVideoId()} maps to Immich asset UUID for later stream/playback routing.
 * Implements {@link ImmichBackedMediaItem} so fork UI can tag {@code mediaSource = IMMICH}.
 */
public final class ImmichMediaItemAdapter implements MediaItem, ImmichBackedMediaItem {
    private static final String TYPE_ALBUM = "album";

    private final ImmichAsset mAsset;
    private final ImmichAlbum mAlbum;
    private final int mId;

    private ImmichMediaItemAdapter(@Nullable ImmichAsset asset, @Nullable ImmichAlbum album) {
        mAsset = asset;
        mAlbum = album;
        String key = asset != null ? asset.getId() : (album != null ? album.getId() : null);
        mId = key != null ? Math.abs(key.hashCode()) : 0;
    }

    @Nullable
    public static ImmichMediaItemAdapter from(@Nullable ImmichAsset asset) {
        if (asset == null || asset.getId() == null || asset.getId().isEmpty()) {
            return null;
        }
        return new ImmichMediaItemAdapter(asset, null);
    }

    /**
     * Album card that opens the album asset grid via {@link #getReloadPageKey()}.
     */
    @Nullable
    public static ImmichMediaItemAdapter fromAlbumBrowse(@Nullable ImmichAlbum album) {
        if (album == null || album.getId() == null || album.getId().isEmpty()) {
            return null;
        }
        return new ImmichMediaItemAdapter(null, album);
    }

    @Nullable
    public ImmichAsset getImmichAsset() {
        return mAsset;
    }

    @Nullable
    public ImmichAlbum getImmichAlbum() {
        return mAlbum;
    }

    private boolean isAlbumBrowse() {
        return mAlbum != null && mAsset == null;
    }

    @Override
    public int getType() {
        return isAlbumBrowse() ? TYPE_PLAYLIST : TYPE_VIDEO;
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public boolean isUpcoming() {
        return false;
    }

    @Override
    public boolean isShorts() {
        return false;
    }

    @Override
    public int getPercentWatched() {
        return -1;
    }

    @Override
    public int getStartTimeSeconds() {
        return -1;
    }

    @Override
    public String getAuthor() {
        return null;
    }

    @Override
    public String getFeedbackToken() {
        return null;
    }

    @Override
    public String getFeedbackToken2() {
        return null;
    }

    @Override
    public String getPlaylistId() {
        return isAlbumBrowse() ? mAlbum.getId() : null;
    }

    @Override
    public int getPlaylistIndex() {
        return -1;
    }

    @Override
    public String getParams() {
        return isAlbumBrowse() ? TYPE_ALBUM : null;
    }

    @Override
    public String getReloadPageKey() {
        return isAlbumBrowse() ? mAlbum.getId() : null;
    }

    @Override
    public boolean hasNewContent() {
        return false;
    }

    @Override
    public int getId() {
        return mId;
    }

    @Override
    public String getTitle() {
        if (isAlbumBrowse()) {
            return mAlbum.getTitle();
        }
        return mAsset != null ? mAsset.getTitle() : null;
    }

    @Override
    public CharSequence getSecondTitle() {
        if (isAlbumBrowse()) {
            int count = mAlbum.getAssetCount();
            return count > 0 ? String.valueOf(count) : null;
        }
        return null;
    }

    @Override
    public String getVideoId() {
        if (isAlbumBrowse()) {
            return null;
        }
        return mAsset != null ? mAsset.getId() : null;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public long getDurationMs() {
        if (isAlbumBrowse() || mAsset == null) {
            return 0L;
        }
        return mAsset.getDurationMs();
    }

    @Override
    public String getBadgeText() {
        return null;
    }

    @Override
    public String getProductionDate() {
        return null;
    }

    @Override
    public long getPublishedDate() {
        return -1;
    }

    @Override
    public String getCardImageUrl() {
        if (isAlbumBrowse()) {
            return mAlbum.getThumbUrl();
        }
        return mAsset != null ? mAsset.getThumbUrl() : null;
    }

    @Override
    public String getBackgroundImageUrl() {
        return getCardImageUrl();
    }

    @Override
    public int getWidth() {
        return 1280;
    }

    @Override
    public int getHeight() {
        return 720;
    }

    @Override
    public String getChannelId() {
        return null;
    }

    @Override
    public String getVideoPreviewUrl() {
        return null;
    }

    @Override
    public String getAudioChannelConfig() {
        return "2.0";
    }

    @Override
    public String getPurchasePrice() {
        return "$0.00";
    }

    @Override
    public String getRentalPrice() {
        return "$0.00";
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public int getRatingStyle() {
        return Rating.RATING_5_STARS;
    }

    @Override
    public double getRatingScore() {
        return 0;
    }

    /**
     * Must stay {@code false}. SmartTube's {@code Video.isEmpty()} treats {@code isMovie}
     * as YouTube "Free with Ads" and drops the card.
     */
    @Override
    public boolean isMovie() {
        return false;
    }

    @Override
    public boolean hasUploads() {
        return isAlbumBrowse();
    }

    @Override
    public String getClickTrackingParams() {
        return null;
    }

    @Override
    public String getSearchQuery() {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MediaItem) {
            MediaItem other = (MediaItem) obj;
            if (isAlbumBrowse()) {
                String playlistId = getPlaylistId();
                return playlistId != null && playlistId.equals(other.getPlaylistId());
            }
            String videoId = getVideoId();
            return videoId != null && videoId.equals(other.getVideoId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (isAlbumBrowse()) {
            String playlistId = getPlaylistId();
            return playlistId != null ? playlistId.hashCode() : 0;
        }
        String videoId = getVideoId();
        return videoId != null ? videoId.hashCode() : 0;
    }
}
