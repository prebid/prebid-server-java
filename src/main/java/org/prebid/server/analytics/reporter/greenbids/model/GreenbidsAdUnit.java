package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GreenbidsAdUnit {

    String code;

    @JsonProperty("unifiedCode")
    GreenbidsUnifiedCode unifiedCode;

    @JsonProperty("mediaTypes")
    MediaTypes mediaTypes;

    List<GreenbidsBids> bids;
}
