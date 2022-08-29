package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;

import java.util.List;

/**
 * This object should be included if the ad supported content is a non-browser
 * application (typically in mobile) as opposed to a website. A bid request must
 * not contain both an {@link App} and a {@link Site} object. At a minimum, it
 * is useful to provide an Application ID or bundle, but this is not strictly required.
 */
@Builder(toBuilder = true)
@Value
public class App {

    /**
     * Exchange-specific app ID.
     */
    String id;

    /**
     * Application name (may be aliased at the publisher’s request).
     */
    String name;

    /**
     * The store ID of the app in an app store. See <a href="https://iabtechlab.com/wp-content/uploads/2020/08/IAB-Tech-Lab-OTT-store-assigned-App-Identification-Guidelines-2020.pdf">
     * OTT/CTV Store Assigned App Identification Guidelines</a> for more details about
     * expected strings for CTV app stores. For mobile apps in Google Play Store,
     * these should be bundle or package names (e.g. com.foo.mygame). For apps in
     * Apple App Store, these should be a numeric ID.
     */
    String bundle;

    /**
     * Domain of the app (e.g., “mygame.foo.com”).
     */
    String domain;

    /**
     * Application store URL for an installed app; for IQG 2.1 compliance.
     */
    String storeurl;

    /**
     * The taxonomy in use. Refer to the AdCOM list <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_categorytaxonomies">List: Category
     * Taxonomies</a> for values.
     */
    Integer cattax;

    /**
     * Array of IAB content categories of the app. The taxonomy to be used
     * is defined by the cattax field. If no cattax field is supplied IAB
     * Content Category Taxonomy 1.0 is assumed.
     */
    List<String> cat;

    /**
     * Array of IAB content categories that describe the current section of
     * the app. The taxonomy to be used is defined by the cattax field.
     */
    List<String> sectioncat;

    /**
     * Array of IAB content categories that describe the current page or view of
     * the app. The taxonomy to be used is defined by the cattax field.
     */
    List<String> pagecat;

    /**
     * Application version.
     */
    String ver;

    /**
     * Indicates if the app has a privacy policy, where 0 = no, 1 = yes.
     */
    Integer privacypolicy;

    /**
     * 0 = app is free, 1 = the app is a paid version.
     */
    Integer paid;

    /**
     * Details about the {@link Publisher} (Section 3.2.15) of the app.
     */
    Publisher publisher;

    /**
     * Details about the {@link Content} (Section 3.2.16) within the app.
     */
    Content content;

    /**
     * Comma separated list of keywords about the app. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    String keywords;

    /**
     * Array of keywords about the site. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    List<String> kwarray;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ExtApp ext;
}
