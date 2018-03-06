package org.prebid.server.proto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class BidderInfo {

    MaintainerInfo maintainer;

    CapabilitiesInfo capabilities;

    public static BidderInfo create(String maintainerEmail, List<String> appMediaTypes, List<String> siteMediaTypes) {
        final MaintainerInfo maintainer = new MaintainerInfo(maintainerEmail);
        final CapabilitiesInfo capabilities = new CapabilitiesInfo(platformInfo(appMediaTypes),
                platformInfo(siteMediaTypes));

        return new BidderInfo(maintainer, capabilities);
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
