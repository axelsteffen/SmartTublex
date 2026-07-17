package de.developerleipzig.immichserviceinterfaces;

import de.developerleipzig.immichserviceinterfaces.data.ImmichAlbum;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAssetPage;

import java.util.List;

import io.reactivex.Observable;

/**
 * Browse Immich albums and fetch asset pages (lazy / on-demand).
 */
public interface ImmichLibraryService {
    Observable<List<ImmichAlbum>> getAlbumsObserve();

    /**
     * Paginated assets in an album.
     *
     * @param offset zero-based item offset (converted to Immich 1-based page internally)
     */
    Observable<ImmichAssetPage> getAlbumAssetsPageObserve(ImmichAlbum album, int offset);

    /**
     * Recent video assets across the library ({@code type=VIDEO}).
     *
     * @param offset zero-based item offset
     */
    Observable<ImmichAssetPage> getRecentVideosPageObserve(int offset);
}
