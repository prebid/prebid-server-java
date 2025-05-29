package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.ExcludeBiddersFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.AdUnitCodeFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.ChannelFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DataCenterFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DeviceCountryFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DeviceCountryInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.MediaTypeInFunction;
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
            DeviceCountryFunction.NAME, new DeviceCountryFunction(),
            DeviceCountryInFunction.NAME, new DeviceCountryInFunction(),
            MediaTypeInFunction.NAME, new MediaTypeInFunction(),
            DataCenterFunction.NAME, new DataCenterFunction(),
            ChannelFunction.NAME, new ChannelFunction(),
            AdUnitCodeFunction.NAME, new AdUnitCodeFunction());

    private static final Map<String, ResultFunction<BidRequest, AuctionContext>> RESULT_FUNCTIONS = Map.of(
            "excludeBidders", ExcludeBiddersFunction.of(ObjectMapperProvider.mapper()),
            "includeBidders", ExcludeBiddersFunction.of(ObjectMapperProvider.mapper()));

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
