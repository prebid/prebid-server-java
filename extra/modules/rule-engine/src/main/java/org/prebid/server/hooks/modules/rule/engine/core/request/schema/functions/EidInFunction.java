package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

import java.util.List;

public class EidInFunction implements SchemaFunction<RequestContext> {

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        return "";
    }

    @Override
    public void validateConfigArguments(List<JsonNode> configArguments) {
        SchemaFunction.super.validateConfigArguments(configArguments);
    }
}
