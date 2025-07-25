package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

@Builder
@Jacksonized
@Value(staticConstructor = "of")
public class AccountConfig {

    @Builder.Default
    boolean enabled = true;

    @Builder.Default
    Instant timestamp = Instant.EPOCH;

    @JsonProperty("ruleSets")
    List<RuleSetConfig> ruleSets;
}
