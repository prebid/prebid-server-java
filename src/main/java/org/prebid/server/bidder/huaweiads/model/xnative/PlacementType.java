package org.prebid.server.bidder.huaweiads.model.xnative;

/**
 * The FORMAT of the ad you are purchasing, separate from the surrounding context
 */

public class PlacementType {
    public static final int placementTypeFeed = 1; // In the feed of content - for example as an item inside the organic feed/grid/listing/carousel.
    public static final int placementTypeAtomicContentUnit = 2; // In the atomic unit of the content - IE in the article page or single image page
    public static final int placementTypeOutsideCoreContent = 3; // Outside the core content - for example in the ads section on the right rail, as a banner-style placement near the content, etc.
    public static final int  placementTypeRecommendationWidget = 4; // Recommendation widget, most commonly presented below the article content.
}

