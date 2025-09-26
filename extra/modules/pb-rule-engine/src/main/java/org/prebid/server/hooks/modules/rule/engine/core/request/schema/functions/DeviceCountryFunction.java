package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.Optional;

public class DeviceCountryFunction implements SchemaFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "deviceCountry";

    @Override
    public String extract(SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        return Optional.of(arguments.getOperand())
                .map(BidRequest::getDevice)
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .orElse(UNDEFINED_RESULT);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
