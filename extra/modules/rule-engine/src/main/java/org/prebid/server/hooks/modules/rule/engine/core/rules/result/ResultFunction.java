package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import java.util.function.Function;

public interface ResultFunction<T, R> extends Function<T, R> {
}
