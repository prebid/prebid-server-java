package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class GreenbidsAnalyticsProperties {

    @JsonProperty("pbuid")
    String pbuid;

    @JsonProperty("greenbidsSampling")
    Double greenbidsSampling;

    @JsonProperty("exploratorySamplingSplit")
    Double exploratorySamplingSplit;

    @JsonProperty("analyticsServerVersion")
    String analyticsServerVersion;

    @JsonProperty("analyticsServer")
    String analyticsServer;

    @JsonProperty("configurationRefreshDelayMs")
    Long configurationRefreshDelayMs;

    @JsonProperty("timeoutMs")
    Long timeoutMs;
}
