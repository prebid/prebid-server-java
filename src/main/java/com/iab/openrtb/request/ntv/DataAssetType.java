package com.iab.openrtb.request.ntv;

/**
 * Below is a list of common asset element types of native advertising at the time of writing this spec.
 * This list is non-exhaustive and intended to be extended by the buyers and sellers as the format evolves.
 */
public enum DataAssetType {

    /**
     * Sponsored By message where response should contain the brand name of the sponsor.
     */
    SPONSORED(1),
    /**
     * Descriptive text associated with the product or service being advertised.
     * Longer length of text in response may be truncated or ellipsed by th exchange.
     */
    DESC(2),
    /**
     * Rating of the product being offered to the user.
     * For example an app’s rating in an app store from 0-5.
     */
    RATING(3),
    /**
     * Number of social ratings or “likes” of the product being offered to the user.
     */
    LIKES(4),
    /**
     * Number downloads/installs of this product
     */
    DOWNLOADS(5),
    /**
     * Price for product / app / in-app purchase.
     * Value should include currency symbol in localised format.
     */
    PRICE(6),
    /**
     * Sale price that can be used together with price to indicate a discounted price compared to a regular price.
     * Value should include currency symbol in localised format.
     */
    SALE_PRICE(7),
    /**
     * Phone number formatted
     */
    PHONE(8),
    /**
     * Address
     */
    ADDRESS(9),
    /**
     * Additional descriptive text associated with the product or service being advertised
     */
    DESC2(10),
    /**
     * Display URL for the text ad.
     * To be used when sponsoring entity doesn’t own the content.
     * IE sponsored by BRAND on SITE (where SITE is transmitted in this field).
     */
    DISPLAY_URL(11),
    /**
     * CTA description - descriptive text describing a ‘call to action’ button for the destination URL.
     */
    CTA_TEXT(12);

    private final Integer value;

    DataAssetType(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
