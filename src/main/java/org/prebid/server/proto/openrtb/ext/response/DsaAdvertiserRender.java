package org.prebid.server.proto.openrtb.ext.response;

public enum DsaAdvertiserRender {

    NOT_RENDER(0),
    WILL_RENDER(1);

    private final int value;

    DsaAdvertiserRender(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
