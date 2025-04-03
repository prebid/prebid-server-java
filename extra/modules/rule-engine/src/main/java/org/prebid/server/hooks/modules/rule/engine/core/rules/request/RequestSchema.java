package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

import java.util.List;
import java.util.Optional;

public class RequestSchema {

    private RequestSchema() {
    }

    public static String deviceCountryExtractor(SchemaFunctionArguments<BidRequest> arguments) {
        final BidRequest bidRequest = arguments.getOperand();
        final List<JsonNode> args = arguments.getArgs();
        if (CollectionUtils.isEmpty(args)) {
            return Optional.ofNullable(bidRequest.getDevice())
                    .map(Device::getGeo)
                    .map(Geo::getCountry)
                    .orElse(null);
        }

        return "true";
    }
}
