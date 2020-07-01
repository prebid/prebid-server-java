package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.json.CommaSeparatedStringArrayDeserializer;
import org.prebid.server.json.StringArrayToFirstItemDeserializer;
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

    /** Exchange-specific app ID. (recommended) */
    @JsonDeserialize(using = StringArrayToFirstItemDeserializer.class)
    String id;

    /** Application name (may be aliased at the publisher’s request). */
    @JsonDeserialize(using = StringArrayToFirstItemDeserializer.class)
    String name;

    /**
     * A platform-specific application identifier intended to be unique to the
     * app and independent of the exchange. On Android, this should be a bundle
     * or package name (e.g., com.foo.mygame). On iOS, it is typically a numeric
     * ID.
     */
    @JsonDeserialize(using = StringArrayToFirstItemDeserializer.class)
    String bundle;

    /** Domain of the app (e.g., “mygame.foo.com”). */
    @JsonDeserialize(using = StringArrayToFirstItemDeserializer.class)
    String domain;

    /** Application store URL for an installed app; for IQG 2.1 compliance. */
    @JsonDeserialize(using = StringArrayToFirstItemDeserializer.class)
    String storeurl;

    /** Array of IAB content categories of the app. Refer to List 5.1. */
    List<String> cat;

    /**
     * Array of IAB content categories that describe the current section of
     * the app. Refer to List 5.1.
     */
    List<String> sectioncat;

    /**
     * Array of IAB content categories that describe the current page or view of
     * the app. Refer to List 5.1.
     */
    List<String> pagecat;

    /** Application version. */
    String ver;

    /** Indicates if the app has a privacy policy, where 0 = no, 1 = yes. */
    Integer privacypolicy;

    /** 0 = app is free, 1 = the app is a paid version. */
    Integer paid;

    /** Details about the Publisher (Section 3.2.15) of the app. */
    Publisher publisher;

    /** Details about the Content (Section 3.2.16) within the app. */
    Content content;

    /** Comma separated list of keywords about the app. */
    @JsonDeserialize(using = CommaSeparatedStringArrayDeserializer.class)
    String keywords;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ExtApp ext;
}
