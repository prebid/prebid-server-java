package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ResultFunctionHolder<R> {

    String rule;

    ResultFunction<List<ResultFunctionArguments>, R> function;

    List<ResultFunctionArguments> arguments;
}
