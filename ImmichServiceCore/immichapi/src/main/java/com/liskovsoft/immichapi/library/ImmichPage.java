package com.liskovsoft.immichapi.library;

import com.liskovsoft.immichserviceinterfaces.data.ImmichAsset;
import com.liskovsoft.immichserviceinterfaces.data.ImmichAssetPage;

import java.util.Collections;
import java.util.List;

/**
 * One page of Immich assets plus next-offset helper for adapters.
 */
public final class ImmichPage implements ImmichAssetPage {
    private final List<ImmichAsset> mItems;
    private final int mOffset;
    private final int mTotalSize;
    private final int mPageSize;

    public ImmichPage(List<ImmichAsset> items, int offset, int totalSize, int pageSize) {
        mItems = items != null ? items : Collections.<ImmichAsset>emptyList();
        mOffset = Math.max(0, offset);
        mTotalSize = totalSize;
        mPageSize = pageSize > 0 ? pageSize : mItems.size();
    }

    @Override
    public List<ImmichAsset> getItems() {
        return mItems;
    }

    @Override
    public int getOffset() {
        return mOffset;
    }

    @Override
    public int getTotalSize() {
        return mTotalSize;
    }

    /**
     * Next zero-based offset, or {@code -1} if no more pages.
     */
    public int getNextOffset() {
        if (mItems.isEmpty()) {
            return -1;
        }
        int next = mOffset + mItems.size();
        if (mTotalSize >= 0 && next >= mTotalSize) {
            return -1;
        }
        if (mItems.size() < mPageSize) {
            return -1;
        }
        return next;
    }
}
