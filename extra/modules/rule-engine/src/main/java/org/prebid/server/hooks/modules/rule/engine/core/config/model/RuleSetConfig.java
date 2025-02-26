package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.hooks.execution.model.Stage;

import java.util.List;

@Value(staticConstructor = "of")
public class RuleSetConfig {

    Stage stage;

    String name;

    String version;

    @JsonProperty("modelGroups")
    List<ModelGroupConfig> modelGroups;
}
