package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class GreenbidsAnalyticsProperties {

    Double exploratorySamplingSplit;

    Double defaultSamplingRate;

    String analyticsServerVersion;

    String analyticsServerUrl;

    Long configurationRefreshDelayMs;

    Long timeoutMs;
}
