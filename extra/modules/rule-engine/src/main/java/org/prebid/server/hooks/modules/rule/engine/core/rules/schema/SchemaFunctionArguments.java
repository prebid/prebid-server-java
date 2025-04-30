package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SchemaFunctionArguments<T> {

    T operand;

    List<JsonNode> configArguments;
}
