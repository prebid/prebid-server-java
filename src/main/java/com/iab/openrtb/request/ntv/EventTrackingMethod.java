package com.iab.openrtb.request.ntv;

public enum EventTrackingMethod {

    /**
     * Image-pixel tracking - URL provided will be inserted as a 1x1 pixel at the time of the event.
     */
    IMAGE(1),
    /**
     * Javascript-based tracking - URL provided will be inserted as a js tag at the time of the event.
     */
    JS(2);

    private final Integer value;

    EventTrackingMethod(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
