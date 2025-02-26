package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value(staticConstructor = "of")
public class AccountConfig {

    boolean enabled;

    Instant timestamp;

    @JsonProperty("ruleSets")
    List<RuleSetConfig> ruleSets;
}
