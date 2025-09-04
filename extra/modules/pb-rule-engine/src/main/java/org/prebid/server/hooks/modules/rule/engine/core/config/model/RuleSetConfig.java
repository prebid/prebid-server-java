package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.prebid.server.hooks.execution.model.Stage;

import java.util.List;

@Value
@Builder
@Jacksonized
public class RuleSetConfig {

    @Builder.Default
    boolean enabled = true;

    Stage stage;

    String name;

    String version;

    @JsonProperty("modelGroups")
    List<ModelGroupConfig> modelGroups;
}
