package org.prebid.server.proto.openrtb.ext.request;

public enum DsaPublisherRender {

    NOT_RENDER(0),
    COULD_RENDER(1),
    WILL_RENDER(2);

    private final int value;

    DsaPublisherRender(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
