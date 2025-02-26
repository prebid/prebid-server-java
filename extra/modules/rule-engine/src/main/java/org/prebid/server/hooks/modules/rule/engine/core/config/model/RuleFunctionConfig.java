package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class RuleFunctionConfig {

    String function;

    JsonNode args;
}
