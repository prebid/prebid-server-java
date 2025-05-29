package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

import java.util.Optional;

public class DeviceCountryFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "deviceCountry";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        return Optional.of(arguments.getOperand().getBidRequest())
                .map(BidRequest::getDevice)
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .orElse(UNDEFINED_RESULT);
    }
}
