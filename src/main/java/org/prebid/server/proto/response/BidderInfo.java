package org.prebid.server.proto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class BidderInfo {

    boolean enabled;

    MaintainerInfo maintainer;

    CapabilitiesInfo capabilities;

    List<String> vendors;

    public static BidderInfo create(boolean enabled, String maintainerEmail, List<String> appMediaTypes,
                                    List<String> siteMediaTypes,
                                    List<String> supportedVendors) {
        final MaintainerInfo maintainer = new MaintainerInfo(maintainerEmail);
        final CapabilitiesInfo capabilities = new CapabilitiesInfo(platformInfo(appMediaTypes),
                platformInfo(siteMediaTypes));

        return new BidderInfo(enabled, maintainer, capabilities, supportedVendors);
    }

    private static PlatformInfo platformInfo(List<String> mediaTypes) {
        return mediaTypes != null ? new PlatformInfo(mediaTypes) : null;
    }

    @Value
    private static class MaintainerInfo {

        String email;
    }

    @Value
    private static class PlatformInfo {

        @JsonProperty("mediaTypes")
        List<String> mediaTypes;
    }

    @Value
    private static class CapabilitiesInfo {

        PlatformInfo app;

        PlatformInfo site;
    }
}
