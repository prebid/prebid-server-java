package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import lombok.Value;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.ResultFunctionArguments;

import java.util.List;

@Value(staticConstructor = "of")
public class RuleAction<T> {

    ResultFunction<T> function;

    List<ResultFunctionArguments> arguments;
}
