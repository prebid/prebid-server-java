package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.AvailableFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.ExtractingFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.InFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@UtilityClass
public class RequestSchemaFunctions {

    public static final String DEVICE_COUNTRY_IN_FUNCTION_NAME = "deviceCountryIn";
    public static final String DATACENTER_IN_FUNCTION_NAME = "datacenterIn";
    public static final String EID_IN_FUNCTION_NAME = "eidIn";
    public static final String EID_AVAILABLE_FUNCTION_NAME = "eidAvailable";
    public static final String AD_UNIT_CODE_FUNCTION_NAME = "adUnitCode";

    public static final SchemaFunction<RequestContext> DEVICE_COUNTRY_IN_FUNCTION = InFunction.of(
            RequestSchemaFunctions::extractDeviceCountry);
    public static final SchemaFunction<RequestContext> DATACENTER_IN_FUNCTION = InFunction.of(
            RequestSchemaFunctions::extractDataCenter);
   public static final SchemaFunction<RequestContext> EID_AVAILABLE_FUNCTION = AvailableFunction.of(
            RequestSchemaFunctions::isEidAvailable);

    private static String extractDeviceCountry(RequestContext context) {
        return Optional.ofNullable(context.getBidRequest().getDevice())
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .orElse(SchemaFunction.UNDEFINED_RESULT);
    }

    private static String extractDataCenter(RequestContext context) {
        return ObjectUtils.defaultIfNull(context.getDatacenter(), SchemaFunction.UNDEFINED_RESULT);
    }

    private static boolean isEidAvailable(RequestContext context) {
        return Optional.of(context.getBidRequest())
                .map(BidRequest::getUser)
                .map(User::getEids)
                .filter(Predicate.not(List::isEmpty))
                .isPresent();
    }
}
