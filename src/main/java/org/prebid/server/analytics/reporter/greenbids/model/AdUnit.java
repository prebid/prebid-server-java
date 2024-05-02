package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class AdUnit {

    @JsonProperty("code")
    String code;

    @JsonProperty("mediaTypes")
    MediaTypes mediaTypes;

    @JsonProperty("bidders")
    List<GreenbidsBidder> bidders;
}



