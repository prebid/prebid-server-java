package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;

import java.util.List;

public interface ResultFunction<T> {

    RuleResult<T> apply(ResultFunctionArguments<T> arguments);

    void validateConfigArguments(List<JsonNode> configArguments);
}
