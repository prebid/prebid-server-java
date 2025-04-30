package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class RuleAction<T> {

    ResultFunction<T> function;

    List<JsonNode> configArguments;
}
