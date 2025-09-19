package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

import java.util.Optional;

public class DeviceTypeInFunction implements SchemaFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "deviceTypeIn";

    public static final String TYPES_FIELD = "types";

    @Override
    public String extract(SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        final Integer deviceType = Optional.ofNullable(arguments.getOperand().getDevice())
                .map(Device::getDevicetype)
                .orElse(null);

        if (deviceType == null) {
            return Boolean.FALSE.toString();
        }

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(TYPES_FIELD).elements())
                .map(JsonNode::asInt)
                .anyMatch(deviceType::equals);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfIntegers(config, TYPES_FIELD);
    }
}
