package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

public class DataCenterInFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "dataCenterIn";

    private static final String DATACENTERS_FIELD = "datacenters";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        final String datacenter = StringUtils.defaultIfEmpty(arguments.getOperand().getDatacenter(), UNDEFINED_RESULT);

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(DATACENTERS_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(datacenter::equalsIgnoreCase);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, DATACENTERS_FIELD);
    }
}
