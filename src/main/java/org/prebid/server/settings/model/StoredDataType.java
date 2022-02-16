package org.prebid.server.settings.model;

/**
 * Defines the type of stored data, used in creating {@link StoredDataResult}.
 */
public enum StoredDataType {

    REQUEST, IMP, SEATBID;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
