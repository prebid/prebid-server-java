package org.prebid.server.proto.openrtb.ext.request;

public enum DsaRequired {

    NOT_REQUIRED(0),
    SUPPORTED(1),
    REQUIRED(2),
    REQUIRED_ONLINE_PLATFORM(3);

    private final Integer value;

    DsaRequired(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
