package org.prebid.server.bidder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.List;

@Value(staticConstructor = "of")
public class BidderInfo {

    boolean enabled;

    OrtbVersion ortbVersion;

    boolean debugAllowed;

    boolean usesHttps;

    String aliasOf;

    MaintainerInfo maintainer;

    CapabilitiesInfo capabilities;

    List<String> vendors;

    GdprInfo gdpr;

    boolean ccpaEnforced;

    boolean modifyingVastXmlAllowed;

    CompressionType compressionType;

    public static BidderInfo create(boolean enabled,
                                    OrtbVersion ortbVersion,
                                    boolean debugAllowed,
                                    String endpoint,
                                    String aliasOf,
                                    String maintainerEmail,
                                    List<MediaType> appMediaTypes,
                                    List<MediaType> siteMediaTypes,
                                    List<String> supportedVendors,
                                    int vendorId,
                                    boolean ccpaEnforced,
                                    boolean modifyingVastXmlAllowed,
                                    CompressionType compressionType) {

        return of(
                enabled,
                ortbVersion,
                debugAllowed,
                StringUtils.startsWith(endpoint, "https://"),
                aliasOf,
                new MaintainerInfo(maintainerEmail),
                new CapabilitiesInfo(platformInfo(appMediaTypes), platformInfo(siteMediaTypes)),
                supportedVendors,
                new GdprInfo(vendorId),
                ccpaEnforced,
                modifyingVastXmlAllowed,
                compressionType);
    }

    private static PlatformInfo platformInfo(List<MediaType> mediaTypes) {
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
    public static class PlatformInfo {

        @JsonProperty("mediaTypes")
        List<MediaType> mediaTypes;
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
