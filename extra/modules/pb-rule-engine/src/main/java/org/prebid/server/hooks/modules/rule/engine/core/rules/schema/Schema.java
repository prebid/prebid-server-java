package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Schema<T, C> {

    List<SchemaFunctionHolder<T, C>> functions;
}
