package org.prebid.server.auction.model;

import com.iab.openrtb.request.Content;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class SiteFpd {

    public static final SiteFpd EMPTY = SiteFpd.builder().build();

    /**
     * Domain of the site (e.g., “mysite.foo.com”).
     */
    String domain;

    /**
     * Array of IAB content categories of the site. Refer to List 5.1.
     */
    List<String> cat;

    /**
     * Array of IAB content categories that describe the current section of the
     * site. Refer to List 5.1.
     */
    List<String> sectioncat;

    /**
     * Array of IAB content categories that describe the current page or view of
     * the site. Refer to List 5.1.
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
     * Details about the Content (Section 3.2.16) within the site.
     */
    Content content;

    /**
     * Comma separated list of keywords about the site.
     */
    String keywords;
}
