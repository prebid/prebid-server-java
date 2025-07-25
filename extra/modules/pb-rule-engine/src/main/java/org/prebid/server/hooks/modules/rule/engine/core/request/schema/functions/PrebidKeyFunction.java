package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.Optional;

public class PrebidKeyFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "prebidKey";

    private static final String KEY_FIELD = "key";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        return Optional.ofNullable(arguments.getOperand().getBidRequest().getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getKvps)
                .map(kvps -> kvps.get(arguments.getConfig().get(KEY_FIELD).asText()))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .orElse(UNDEFINED_RESULT);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertString(config, KEY_FIELD);
    }
}
