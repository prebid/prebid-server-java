package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import lombok.Value;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.ResultFunctionArguments;

import java.util.List;

@Value(staticConstructor = "of")
public class RuleResult<T> {

    String ruleFired;

    ResultFunction<T> action;

    List<ResultFunctionArguments> arguments;
}
