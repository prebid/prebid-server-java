package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.ResultFunctionArguments;

public interface ResultFunction<T> {

    ResultFunctionResult<T> apply(ResultFunctionArguments arguments,
                                  InfrastructureArguments infrastructureArguments,
                                  T operand);
}
