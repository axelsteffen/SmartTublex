package de.developerleipzig.immichapi.library;

import de.developerleipzig.immichserviceinterfaces.data.ImmichUser;

/**
 * Immutable {@link ImmichUser}.
 */
public final class ImmichUserImpl implements ImmichUser {
    private final String mId;
    private final String mName;
    private final String mEmail;

    public ImmichUserImpl(String id, String name, String email) {
        mId = id;
        mName = name;
        mEmail = email;
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getEmail() {
        return mEmail;
    }
}
