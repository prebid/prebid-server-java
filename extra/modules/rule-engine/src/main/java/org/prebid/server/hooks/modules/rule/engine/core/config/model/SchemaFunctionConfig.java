package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SchemaFunctionConfig {

    String function;

    List<JsonNode> args;
}
