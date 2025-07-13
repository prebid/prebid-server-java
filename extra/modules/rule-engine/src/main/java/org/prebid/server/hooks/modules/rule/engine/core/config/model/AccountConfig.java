package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountConfig {

    boolean enabled;

    long timestamp;

    @JsonProperty("ruleSets")
    List<RuleSetConfig> ruleSets;
}
