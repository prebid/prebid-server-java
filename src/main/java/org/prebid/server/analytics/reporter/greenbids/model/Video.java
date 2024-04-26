package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class Video {
    @JsonProperty("context")
    String context;

    @JsonProperty("placement")
    Integer placement;

    @JsonProperty("plcmnt")
    Integer plcmnt;

    @JsonProperty("minDuration")
    Integer minDuration;

    @JsonProperty("maxDuration")
    Integer maxDuration;

    @JsonProperty("startDelay")
    Integer startDelay;

    @JsonProperty("skip")
    Integer skip;
}
