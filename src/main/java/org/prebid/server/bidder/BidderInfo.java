package org.prebid.server.bidder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Value(staticConstructor = "of")
public class BidderInfo {

    boolean enabled;

    boolean usesHttps;

    String aliasOf;

    MaintainerInfo maintainer;

    CapabilitiesInfo capabilities;

    List<String> vendors;

    GdprInfo gdpr;

    boolean ccpaEnforced;

    boolean modifyingVastXmlAllowed;

    public static BidderInfo create(boolean enabled,
                                    String endpoint,
                                    String aliasOf,
                                    String maintainerEmail,
                                    List<String> appMediaTypes,
                                    List<String> siteMediaTypes,
                                    List<String> supportedVendors,
                                    int vendorId,
                                    boolean ccpaEnforced,
                                    boolean modifyingVastXmlAllowed) {

        return of(
                enabled,
                StringUtils.startsWith(endpoint, "https://"),
                aliasOf,
                new MaintainerInfo(maintainerEmail),
                new CapabilitiesInfo(platformInfo(appMediaTypes), platformInfo(siteMediaTypes)),
                supportedVendors,
                new GdprInfo(vendorId),
                ccpaEnforced,
                modifyingVastXmlAllowed);
    }

    private static PlatformInfo platformInfo(List<String> mediaTypes) {
        return mediaTypes != null ? new PlatformInfo(mediaTypes) : null;
    }

    @Value
    public static class MaintainerInfo {

        String email;
    }

    @Value
    public static class CapabilitiesInfo {

        PlatformInfo app;

        PlatformInfo site;
    }

    @Value
    private static class PlatformInfo {

        @JsonProperty("mediaTypes")
        List<String> mediaTypes;
    }

    @Value
    public static class GdprInfo {

        /**
         * GDPR Vendor ID in the IAB Global Vendor List which refers to this Bidder.
         * <p>
         * The Global Vendor list can be found at https://iabeurope.eu/
         * Bidders can be registered to the list at https://register.consensu.org/
         * <p>
         * If you're not on the list, this should return 0. If cookie sync requests have GDPR consent info,
         * or the Prebid Server host company configures its deploy to be "cautious" when no GDPR info exists
         * in the request, it will _not_ sync user IDs with you.
         */
        @JsonProperty("vendorId")
        int vendorId;
    }
}
