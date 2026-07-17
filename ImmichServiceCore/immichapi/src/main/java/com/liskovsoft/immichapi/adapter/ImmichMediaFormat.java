package com.liskovsoft.immichapi.adapter;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;

import java.util.Collections;
import java.util.List;

/**
 * Minimal {@link MediaFormat} for Immich Direct Play progressive URLs.
 */
public final class ImmichMediaFormat implements MediaFormat {
    private final String mUrl;
    private final String mMimeType;

    public ImmichMediaFormat(String url, @Nullable String mimeType) {
        mUrl = url;
        mMimeType = mimeType;
    }

    @Override
    public int getFormatType() {
        return FORMAT_TYPE_REGULAR;
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public String getITag() {
        return null;
    }

    @Override
    public boolean isDrc() {
        return false;
    }

    @Override
    public String getClen() {
        return null;
    }

    @Override
    public String getBitrate() {
        return null;
    }

    @Override
    public String getProjectionType() {
        return null;
    }

    @Override
    public String getXtags() {
        return null;
    }

    @Override
    public int getWidth() {
        return -1;
    }

    @Override
    public int getHeight() {
        return -1;
    }

    @Override
    public String getIndex() {
        return null;
    }

    @Override
    public String getInit() {
        return null;
    }

    @Override
    public String getFps() {
        return null;
    }

    @Override
    public String getLmt() {
        return null;
    }

    @Override
    public String getQualityLabel() {
        return null;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public boolean isOtf() {
        return false;
    }

    @Override
    public String getOtfInitUrl() {
        return null;
    }

    @Override
    public String getOtfTemplateUrl() {
        return null;
    }

    @Override
    public String getLanguage() {
        return null;
    }

    @Override
    public int getTargetDurationSec() {
        return -1;
    }

    @Override
    public int getMaxDvrDurationSec() {
        return -1;
    }

    @Override
    public int getApproxDurationMs() {
        return -1;
    }

    @Override
    public String getQuality() {
        return null;
    }

    @Override
    public String getSignature() {
        return null;
    }

    @Override
    public String getAudioSamplingRate() {
        return null;
    }

    @Override
    public String getSourceUrl() {
        return null;
    }

    @Override
    public List<String> getSegmentUrlList() {
        return null;
    }

    @Override
    public List<String> getGlobalSegmentList() {
        return null;
    }

    @Override
    public int compareTo(MediaFormat o) {
        return 0;
    }

    public static List<MediaFormat> singletonList(String url, @Nullable String mimeType) {
        if (url == null || url.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ImmichMediaFormat(url, mimeType));
    }
}
