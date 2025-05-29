package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

public class DataCenterFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "datacenter";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        return StringUtils.defaultIfEmpty(arguments.getOperand().getDatacenter(), UNDEFINED_RESULT);
    }
}
