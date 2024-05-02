package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class GreenbidsAnalyticsProperties {

    String pbuid;
    Double greenbidsSampling;
    Double exploratorySamplingSplit;
    Long configurationRefreshDelayMs;
    Long timeoutMs;
}
