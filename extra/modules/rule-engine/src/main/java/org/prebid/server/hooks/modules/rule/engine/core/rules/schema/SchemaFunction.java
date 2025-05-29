package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface SchemaFunction<T> {

    String UNDEFINED_RESULT = "undefined";

    String extract(SchemaFunctionArguments<T> arguments);

    default void validateConfigArguments(ObjectNode args) {
    }
}
