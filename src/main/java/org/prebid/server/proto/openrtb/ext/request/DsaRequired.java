package org.prebid.server.proto.openrtb.ext.request;

public enum DsaRequired {

    NOT_REQUIRED(0),
    SUPPORTED(1),
    REQUIRED(2),
    REQUIRED_ONLINE_PLATFORM(3);

    private final int value;

    DsaRequired(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
