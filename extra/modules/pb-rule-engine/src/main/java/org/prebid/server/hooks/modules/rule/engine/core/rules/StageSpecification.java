package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;

public interface StageSpecification<T, C> {

    SchemaFunction<T, C> schemaFunctionByName(String name);

    ResultFunction<T, C> resultFunctionByName(String name);
}
