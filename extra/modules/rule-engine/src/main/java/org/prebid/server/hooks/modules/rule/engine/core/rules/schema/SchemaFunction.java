package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface SchemaFunction<T> {

    String UNDEFINED_RESULT = "undefined";

    String extract(SchemaFunctionArguments<T> arguments);

    default void validateConfigArguments(List<JsonNode> configArguments) {
    }
}
