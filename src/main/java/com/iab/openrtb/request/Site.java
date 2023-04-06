package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;

import java.util.List;

/**
 * This object should be included if the ad supported content is a website as
 * opposed to a non-browser application. A bid request must not contain both a
 * {@link Site} and an {@link App} object. At a minimum, it is useful to provide
 * a site ID or page URL, but this is not strictly required.
 */
@Builder(toBuilder = true)
@Value
public class Site {

    /**
     * Exchange-specific site ID.
     */
    String id;

    /**
     * Site name (may be aliased at the publisher’s request).
     */
    String name;

    /**
     * Domain of the site (e.g., “mysite.foo.com”).
     */
    String domain;

    /**
     * The taxonomy in use. Refer to the AdCOM list <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_categorytaxonomies">List: Category
     * Taxonomies</a> for values. If no cattax field is supplied IAB Content
     * Category Taxonomy 1.0 is assumed.
     */
    Integer cattax;

    /**
     * Array of IABTL content categories of the site.
     * The taxonomy to be used is defined by the cattax field.
     */
    List<String> cat;

    /**
     * Array of IABTL content categories that describe the current section of the
     * site. The taxonomy to be used is defined by the cattax field.
     */
    List<String> sectioncat;

    /**
     * Array of IABTL content categories that describe the current page or view of
     * the site. The taxonomy to be used is defined by the cattax field
     */
    List<String> pagecat;

    /**
     * URL of the page where the impression will be shown.
     */
    String page;

    /**
     * Referrer URL that caused navigation to the current page.
     */
    String ref;

    /**
     * Search string that caused navigation to the current page.
     */
    String search;

    /**
     * Indicates if the site has been programmed to optimize layout when viewed
     * on mobile devices, where 0 = no, 1 = yes.
     */
    Integer mobile;

    /**
     * Indicates if the site has a privacy policy, where 0 = no, 1 = yes.
     */
    Integer privacypolicy;

    /**
     * Details about the {@link Publisher} (Section 3.2.15) of the site.
     */
    Publisher publisher;

    /**
     * Details about the {@link Content} (Section 3.2.16) within the site.
     */
    Content content;

    /**
     * Comma separated list of keywords about the site. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    String keywords;

    /**
     * Array of keywords about the site. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    List<String> kwarray;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ExtSite ext;
}
