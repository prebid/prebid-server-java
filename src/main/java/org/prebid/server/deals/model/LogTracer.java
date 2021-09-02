package org.prebid.server.deals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class LogTracer {

    String cmd;

    Boolean raw;

    @JsonProperty("durationInSeconds")
    Long durationInSeconds;

    LogCriteriaFilter filters;
}
