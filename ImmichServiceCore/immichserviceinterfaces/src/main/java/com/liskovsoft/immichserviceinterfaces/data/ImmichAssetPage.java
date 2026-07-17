package com.liskovsoft.immichserviceinterfaces.data;

import java.util.List;

/** One page of Immich assets (fork-only). */
public interface ImmichAssetPage {
    List<ImmichAsset> getItems();

    /** Zero-based offset of this page. */
    int getOffset();

    /** Total matching assets when known; -1 if unknown. */
    int getTotalSize();
}
