package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Value
@Builder
@Jacksonized
public class AccountConfig {

    @Builder.Default
    boolean enabled = true;

    @Builder.Default
    Instant timestamp = Instant.EPOCH;

    @Builder.Default
    @JsonProperty("ruleSets")
    List<RuleSetConfig> ruleSets = Collections.emptyList();
}
