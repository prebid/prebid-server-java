package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

public class DataCenterFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "datacenter";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        return StringUtils.defaultIfEmpty(arguments.getOperand().getDatacenter(), UNDEFINED_RESULT);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
