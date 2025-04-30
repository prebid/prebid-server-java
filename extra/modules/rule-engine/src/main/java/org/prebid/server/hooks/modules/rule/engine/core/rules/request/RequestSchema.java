package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequestSchema {

    private RequestSchema() {
    }

    public static final String DEVICE_COUNTRY_FUNCTION = "deviceCountry";
    public static final String AD_UNIT_CODE_FUNCTION = "adUnitCode";
    public static final String MEDIA_TYPE_FUNCTION = "mediaType";

    private static final Map<String, SchemaFunction<RequestPayload>> SCHEMA_FUNCTIONS = Map.of(
            AD_UNIT_CODE_FUNCTION, RequestSchema::deviceCountryExtractor);

    public static SchemaFunction<RequestPayload> schemaFunctionByName(String function) {
        return SCHEMA_FUNCTIONS.get(function);
    }

    public static ResultFunction<BidRequest> resultFunctionByName(String function) {
        return null;
    }

    public static String deviceCountryExtractor(SchemaFunctionArguments<BidRequest> arguments) {
        final BidRequest bidRequest = arguments.getOperand();
        final List<JsonNode> args = arguments.getConfigArguments();
        if (CollectionUtils.isEmpty(args)) {
            return Optional.ofNullable(bidRequest.getDevice())
                    .map(Device::getGeo)
                    .map(Geo::getCountry)
                    .orElse(null);
        }

        return "true";
    }
}
