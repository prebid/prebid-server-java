package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

public interface SchemaFunction<T> {

    String extract(SchemaFunctionArguments<T> arguments);

    void validate(SchemaFunctionArguments<T> arguments);
}
