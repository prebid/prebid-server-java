package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

import java.util.Optional;

public class DeviceCountryInFunction implements SchemaFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "deviceCountryIn";
    private static final String COUNTRIES_FIELD = "countries";

    @Override
    public String extract(SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        final String deviceCountry = Optional.of(arguments.getOperand())
                .map(BidRequest::getDevice)
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .orElse(UNDEFINED_RESULT);

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(COUNTRIES_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(deviceCountry::equalsIgnoreCase);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, COUNTRIES_FIELD);
    }
}
