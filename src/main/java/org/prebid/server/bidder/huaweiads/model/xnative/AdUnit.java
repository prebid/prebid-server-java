package org.prebid.server.bidder.huaweiads.model.xnative;

/**
Below is a list of the core ad unit ids described by IAB here http://www.iab.net/media/file/IABNativeAdvertisingPlaybook120413.pdf
 In feed unit is essentially a layout, it has been removed from the list.
The in feed units can be identified via the layout parameter on the request.
An implementing exchange may not support all asset variants or introduce new ones unique to that system
*/

public class AdUnit {
    public static final int adUnitPaidSearch = 1; // Paid Search Units
    public static final int adUnitRecommendationWidget =  2; // Recommendation Widgets
    public static final int adUnitPromotedListing = 3; // Promoted Listings
    public static final int adUnitInAd = 4; // In-Ad (IAB Standard) with Native Element Units
    public static final int adUnitCustom = 5; //  Custom /”Can’t Be Contained”
}
