package com.iab.openrtb.request.ntv;

public enum EventType {

    /**
     * Impression
     */
    IMPRESSION(1),
    /**
     * Visible impression using MRC definition at 50% in view for 1 second
     */
    VIEWABLE_MRC50(2),
    /**
     * 100% in view for 1 second (ie GroupM standard)
     */
    VIEWABLE_MRC100(3),
    /**
     * Visible impression for video using MRC definition at 50% in view for 2 seconds
     */
    VIEWABLE_VIDEO50(4);
    private final Integer value;

    EventType(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
