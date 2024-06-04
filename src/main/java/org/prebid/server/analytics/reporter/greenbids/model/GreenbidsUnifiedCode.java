package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class GreenbidsUnifiedCode {

    @JsonProperty("value")
    String value;

    @JsonProperty("src")
    String source;
}
