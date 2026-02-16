package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class SchemaFunctionArguments<T, C> {

    T operand;

    ObjectNode config;

    C context;
}
