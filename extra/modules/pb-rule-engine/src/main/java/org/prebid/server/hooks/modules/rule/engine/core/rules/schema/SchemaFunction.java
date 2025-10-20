package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface SchemaFunction<T, C> {

    String UNDEFINED_RESULT = "undefined";

    String extract(SchemaFunctionArguments<T, C> arguments);

    void validateConfig(ObjectNode config);
}
