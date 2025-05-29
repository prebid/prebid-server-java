package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class ResultFunctionArguments<T, C> {

    T operand;

    JsonNode config;

    InfrastructureArguments<C> infrastructureArguments;
}
