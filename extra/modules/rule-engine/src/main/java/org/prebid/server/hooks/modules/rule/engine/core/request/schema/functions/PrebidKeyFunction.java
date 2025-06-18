package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

public class PrebidKeyFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "prebidKey";

    private static final String KEY_FIELD = "key";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
//       final String key = arguments.getConfig().get(KEY_FIELD).asText();
//       return Optional.ofNullable(arguments.getOperand().getBidRequest().getExt())
//               .map(ext -> ext.getPrebid())
//               .map(e -> e.get)

        return UNDEFINED_RESULT;
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertString(config, KEY_FIELD);
    }
}
