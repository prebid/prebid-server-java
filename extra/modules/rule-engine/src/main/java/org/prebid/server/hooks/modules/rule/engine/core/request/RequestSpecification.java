package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.ExcludeBiddersFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.AdUnitCodeFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.RequestSchemaFunctions;
import org.prebid.server.hooks.modules.rule.engine.core.rules.StageSpecification;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.InvalidSchemaFunctionException;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestSpecification implements StageSpecification<RequestContext, BidRequest, AuctionContext> {

    public static final RequestSpecification INSTANCE = new RequestSpecification();

    public static final Set<String> PER_IMP_SCHEMA_FUNCTIONS = Set.of(AdUnitCodeFunction.NAME);

    private static final Map<String, SchemaFunction<RequestContext>> SCHEMA_FUNCTIONS = Map.of(
            RequestSchemaFunctions.DEVICE_COUNTRY_FUNCTION_NAME, RequestSchemaFunctions.DEVICE_COUNTRY_FUNCTION,
            RequestSchemaFunctions.DEVICE_COUNTRY_IN_FUNCTION_NAME, RequestSchemaFunctions.DEVICE_COUNTRY_IN_FUNCTION,
            AdUnitCodeFunction.NAME, new AdUnitCodeFunction());

    private static final Map<String, ResultFunction<BidRequest, AuctionContext>> RESULT_FUNCTIONS = Map.of(
            "excludeBidders", ExcludeBiddersFunction.of(ObjectMapperProvider.mapper()),
            "includeBidders", ExcludeBiddersFunction.of(ObjectMapperProvider.mapper())
    );

    public SchemaFunction<RequestContext> schemaFunctionByName(String name) {
        final SchemaFunction<RequestContext> function = SCHEMA_FUNCTIONS.get(name);
        if (function == null) {
            throw new InvalidSchemaFunctionException(name);
        }

        return function;
    }

    public ResultFunction<BidRequest, AuctionContext> resultFunctionByName(String name) {
        final ResultFunction<BidRequest, AuctionContext> function = RESULT_FUNCTIONS.get(name);
        if (function == null) {
            throw new InvalidSchemaFunctionException(name);
        }

        return function;
    }
}
