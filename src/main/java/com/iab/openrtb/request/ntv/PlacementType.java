package com.iab.openrtb.request.ntv;

/**
 * The FORMAT of the ad you are purchasing, separate from the surrounding context
 */
public enum PlacementType {

    /**
     * In the feed of content - for example as an item inside the organic feed/grid/listing/carousel.
     */
    FEED(1),
    /**
     * In the atomic unit of the content - IE in the article page or single image page
     */
    ATOMIC_CONTENT_UNIT(2),
    /**
     * Outside the core content - for example in the ads section on the right rail, as a banner-style placement near
     * the content, etc.
     */
    OUTSIDE_CORE_CONTENT(3),
    /**
     * Recommendation widget, most commonly presented below the article content.
     */
    RECOMMENDATION_WIDGET(4);

    private final Integer value;

    PlacementType(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
