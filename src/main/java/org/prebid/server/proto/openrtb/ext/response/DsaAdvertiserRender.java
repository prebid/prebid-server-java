package org.prebid.server.proto.openrtb.ext.response;

public enum DsaAdvertiserRender {

    NOT_RENDER(0),
    WILL_RENDER(1);

    private final Integer value;

    DsaAdvertiserRender(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
