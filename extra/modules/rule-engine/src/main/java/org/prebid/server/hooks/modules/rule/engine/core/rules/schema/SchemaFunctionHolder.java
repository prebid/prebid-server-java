package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SchemaFunctionHolder<T> {

    String name;

    SchemaFunction<T> schemaFunction;

    List<JsonNode> arguments;
}
