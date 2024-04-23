package org.prebid.server.proto.openrtb.ext.request;

public enum DsaPublisherRender {

    NOT_RENDER(0),
    COULD_RENDER(1),
    WILL_RENDER(2);

    private final Integer value;

    DsaPublisherRender(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
