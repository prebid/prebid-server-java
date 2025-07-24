package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;

public interface StageSpecification<SCHEMA_PAYLOAD, RULE_PAYLOAD, CONTEXT> {

    SchemaFunction<SCHEMA_PAYLOAD> schemaFunctionByName(String name);

    ResultFunction<RULE_PAYLOAD, CONTEXT> resultFunctionByName(String name);
}
