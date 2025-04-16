package org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments;

public sealed interface ResultFunctionArguments permits
        ExcludeBiddersArguments, IncludeBiddersArguments, LogATagArguments {
}
