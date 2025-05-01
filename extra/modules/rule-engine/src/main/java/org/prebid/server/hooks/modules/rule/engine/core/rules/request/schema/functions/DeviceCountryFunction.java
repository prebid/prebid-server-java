package org.prebid.server.hooks.modules.rule.engine.core.rules.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.RequestPayload;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

import java.util.List;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DeviceCountryFunction implements SchemaFunction<RequestPayload> {

    public static final String NAME = "deviceCountry";

    public static final DeviceCountryFunction INSTANCE = new DeviceCountryFunction();

    @Override
    public String extract(SchemaFunctionArguments<RequestPayload> arguments) {
        final BidRequest bidRequest = arguments.getOperand().getBidRequest();
        final List<JsonNode> args = arguments.getConfigArguments();
        if (CollectionUtils.isEmpty(args)) {
            return Optional.ofNullable(bidRequest.getDevice())
                    .map(Device::getGeo)
                    .map(Geo::getCountry)
                    .orElse(null);
        }

        return "true";
    }

    @Override
    public void validateConfigArguments(List<JsonNode> configArguments) {

    }
}
