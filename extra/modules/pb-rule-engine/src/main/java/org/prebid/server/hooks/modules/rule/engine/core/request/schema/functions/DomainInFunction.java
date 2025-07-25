package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.util.DomainUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

public class DomainInFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "domainIn";

    private static final String DOMAINS_FIELD = "domains";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        final String domain = DomainUtils.extractDomain(arguments.getOperand().getBidRequest())
                .orElse(UNDEFINED_RESULT);

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(DOMAINS_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(domain::equals);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, DOMAINS_FIELD);
    }
}
