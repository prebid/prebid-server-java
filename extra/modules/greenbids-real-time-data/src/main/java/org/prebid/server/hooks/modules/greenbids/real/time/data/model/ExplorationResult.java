package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

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
