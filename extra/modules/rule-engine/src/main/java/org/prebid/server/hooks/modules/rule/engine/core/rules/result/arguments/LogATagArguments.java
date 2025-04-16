package org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments;

import lombok.Value;

@Value(staticConstructor = "of")
public class LogATagArguments implements ResultFunctionArguments {

    String analyticsValue;
}
