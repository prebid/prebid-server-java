package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

public class EidInFunction implements SchemaFunction<RequestContext> {

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        return "";
    }

    @Override
    public void validateConfigArguments(ObjectNode config) {
        SchemaFunction.super.validateConfigArguments(config);
    }
}
