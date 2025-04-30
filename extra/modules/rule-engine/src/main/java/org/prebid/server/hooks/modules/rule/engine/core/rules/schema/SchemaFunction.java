package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface SchemaFunction<T> {

    String extract(SchemaFunctionArguments<T> arguments);

    void validate(List<JsonNode> configArguments);
}
