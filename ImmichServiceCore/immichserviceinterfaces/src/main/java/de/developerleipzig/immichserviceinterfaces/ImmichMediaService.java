package de.developerleipzig.immichserviceinterfaces;

import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;
import de.developerleipzig.immichserviceinterfaces.data.ImmichStreamInfo;

import io.reactivex.Observable;

/**
 * Resolve playable URLs for an Immich asset (video-first MVP).
 */
public interface ImmichMediaService {
    /**
     * Resolves a playback URL for the asset (video playback endpoint, else original).
     * Callers that feed ExoPlayer must send {@code x-api-key} as a request header —
     * Immich does not accept the key as a URL query param.
     */
    Observable<ImmichStreamInfo> getStreamInfoObserve(ImmichAsset asset);
}
