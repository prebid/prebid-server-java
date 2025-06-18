package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;

public interface ResultFunction<T, C> {

    RuleResult<T> apply(ResultFunctionArguments<T, C> arguments);

    void validateConfig(ObjectNode config);
}
