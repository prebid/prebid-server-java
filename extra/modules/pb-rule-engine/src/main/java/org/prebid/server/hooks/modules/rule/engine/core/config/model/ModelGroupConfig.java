package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class ModelGroupConfig {

    int weight;

    @JsonProperty("analyticsKey")
    String analyticsKey;

    String version;

    List<SchemaFunctionConfig> schema;

    @JsonProperty("default")
    List<ResultFunctionConfig> defaultAction;

    List<AccountRuleConfig> rules;
}
