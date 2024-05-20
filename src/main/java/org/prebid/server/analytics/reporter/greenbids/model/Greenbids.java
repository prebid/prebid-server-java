package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class Greenbids {

    @JsonProperty("fingerprint")
    private String fingerprint;

    @JsonProperty("keptInAuction")
    private Map<String, Boolean> keptInAuction;

    @JsonProperty("isExploration")
    private Boolean isExploration;
}
