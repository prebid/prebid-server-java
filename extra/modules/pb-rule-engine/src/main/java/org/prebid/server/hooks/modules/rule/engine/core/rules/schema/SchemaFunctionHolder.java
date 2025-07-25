package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class SchemaFunctionHolder<T> {

    String name;

    SchemaFunction<T> schemaFunction;

    ObjectNode config;
}
