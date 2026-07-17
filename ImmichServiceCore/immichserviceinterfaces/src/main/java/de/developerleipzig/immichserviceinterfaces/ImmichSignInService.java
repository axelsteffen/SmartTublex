package de.developerleipzig.immichserviceinterfaces;

import de.developerleipzig.immichserviceinterfaces.data.ImmichUser;

import io.reactivex.Observable;

/**
 * Immich connection via server URL + API key ({@code x-api-key}).
 */
public interface ImmichSignInService {
    /** True when URL and API key are set (and preferably validated). */
    boolean isSigned();

    String getServerUrl();

    void setServerUrl(String serverUrl);

    String getApiKey();

    void setApiKey(String apiKey);

    void signOut();

    /**
     * Validates credentials against {@code GET /api/users/me}.
     * On success, persists URL/key and user profile; emits the user.
     */
    Observable<ImmichUser> validateObserve();
}
