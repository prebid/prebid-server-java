package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.ResultFunctionArguments;

public interface ResultFunction<T> {

    RuleResult<T> apply(ResultFunctionArguments arguments,
                        InfrastructureArguments infrastructureArguments,
                        T operand);

    void validateArguments(ResultFunctionArguments arguments);
}
