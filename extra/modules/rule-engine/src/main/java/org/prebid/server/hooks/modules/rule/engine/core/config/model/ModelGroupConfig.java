package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ModelGroupConfig {

    double weight;

    @JsonProperty("analyticsKey")
    String analyticsKey;

    String version;

    List<SchemaFunctionConfig> schema;

    @JsonProperty("default")
    List<RuleFunctionConfig> defaultAction;

    List<AccountRuleConfig> rules;
}
