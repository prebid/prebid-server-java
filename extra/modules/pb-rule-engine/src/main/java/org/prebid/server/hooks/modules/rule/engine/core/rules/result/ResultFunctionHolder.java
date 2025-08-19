package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class ResultFunctionHolder<T, C> {

    String name;

    ResultFunction<T, C> function;

    ObjectNode config;
}
