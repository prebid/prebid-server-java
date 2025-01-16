package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class ExplorationResult {

    String fingerprint;

    @JsonProperty("keptInAuction")
    Map<String, Boolean> keptInAuction;

    @JsonProperty("isExploration")
    Boolean isExploration;
}
