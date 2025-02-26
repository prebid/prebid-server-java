package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ModelGroupConfig {

    int weight;

    @JsonProperty("analyticsKey")
    String analyticsKey;

    String version;

    @JsonProperty("default")
    RuleFunctionConfig defaultAction;

    List<RuleConfig> rules;
}
