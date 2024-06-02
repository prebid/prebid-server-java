package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GreenbidsAdUnit {

    @JsonProperty("code")
    String code;

    @JsonProperty("unifiedCode")
    GreenbidsUnifiedCode unifiedCode;

    @JsonProperty("codeType")
    String codeType;

    @JsonProperty("mediaTypes")
    MediaTypes mediaTypes;

    @JsonProperty("bids")
    List<GreenbidsBids> bids;
}



