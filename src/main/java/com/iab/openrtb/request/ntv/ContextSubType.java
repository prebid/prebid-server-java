package com.iab.openrtb.request.ntv;

/**
 * Next-level context in which the ad appears.
 * Again this reflects the primary context, and does not imply no presence of other elements.
 * For example, an article is likely to contain images but is still first and foremost an article.
 * SubType should only be combined with the primary context type as indicated (ie for a context type of 1,
 * only context subtypes that start with 1 are valid).
 */
public enum ContextSubType {

    /**
     * General or mixed content.
     */
    GENERAL(10),
    /**
     * Primarily article content (which of course could include images, etc as part of the article)
     */
    ARTICLE(11),
    /**
     * Primarily video content
     */
    VIDEO(12),
    /**
     * Primarily audio content
     */
    AUDIO(13),
    /**
     * Primarily image content
     */
    IMAGE(14),
    /**
     * User-generated content - forums, comments, etc
     */
    USER_GENERATED(15),
    /**
     * General social content such as a general social network
     */
    SOCIAL(20),
    /**
     * Primarily email content
     */
    EMAIL(21),
    /**
     * Primarily chat/IM content
     */
    CHAT(22),
    /**
     * Content focused on selling products, whether digital or physical
     */
    SELLING(30),
    /**
     * Application store/marketplace
     */
    APP_STORE(31),
    /**
     * Product reviews site primarily (which may sell product secondarily)
     */
    PRODUCT_REVIEW(32);

    private final Integer value;

    ContextSubType(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
