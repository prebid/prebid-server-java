package org.prebid.server.settings.bidder;

import lombok.Value;

@Value
public class CapabilitiesInfo {

    boolean validateMediaType;

    PlatformInfo app;

    PlatformInfo site;
}
