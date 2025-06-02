package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class RuleAction<T, C> {

    String name;

    ResultFunction<T, C> function;

    JsonNode config;
}
