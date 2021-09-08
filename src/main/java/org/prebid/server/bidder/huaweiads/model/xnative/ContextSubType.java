package org.prebid.server.bidder.huaweiads.model.xnative;

/**
 * Next-level context in which the ad appears.
 * Again this reflects the primary context, and does not imply no presence of other elements.
 * For example, an article is likely to contain images but is still first and foremost an article.
 * SubType should only be combined with the primary context type as indicated (ie for a context type of 1, only context subtypes that start with 1 are valid).
 */

public class ContextSubType {
    public static final int  contextSubTypeGeneral = 10; // General or mixed content.
    public static final int contextSubTypeArticle  = 11; // Primarily article content (which of course could include images, etc as part of the article)
    public static final int  contextSubTypeVideo = 12; // Primarily video content
    public static final int contextSubTypeAudio = 13; // Primarily audio content
    public static final int contextSubTypeImage = 14; // Primarily image content
    public static final int contextSubTypeUserGenerated = 15; // User-generated content - forums, comments, etc
    public static final int contextSubTypeSocial = 20; // General social content such as a general social network
    public static final int contextSubTypeEmail = 21; // Primarily email content
    public static final int contextSubTypeChat = 22; // Primarily chat/IM content
    public static final int contextSubTypeSelling = 30; // Content focused on selling products, whether digital or physical
    public static final int contextSubTypeAppStore = 31; // Application store/marketplace
    public static final int contextSubTypeProductReview = 32; // Product reviews site primarily (which may sell product secondarily)

}
