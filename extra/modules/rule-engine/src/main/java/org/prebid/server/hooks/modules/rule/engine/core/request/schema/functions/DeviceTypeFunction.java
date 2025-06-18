package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Device;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.Optional;

public class DeviceTypeFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "deviceType";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        return Optional.ofNullable(arguments.getOperand().getBidRequest().getDevice())
                .map(Device::getDevicetype)
                .map(String::valueOf)
                .orElse(UNDEFINED_RESULT);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
