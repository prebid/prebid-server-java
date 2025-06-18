package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

import java.util.Optional;

public class BundleInFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "bundleIn";

    private static final String BUNDLES_FIELD = "bundles";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        final String bundle = Optional.ofNullable(arguments.getOperand().getBidRequest().getApp())
                .map(App::getBundle)
                .orElse(UNDEFINED_RESULT);

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(BUNDLES_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(bundle::equals);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, BUNDLES_FIELD);
    }
}
