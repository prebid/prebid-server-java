package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.ExcludeBiddersFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.IncludeBiddersFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.AdUnitCodeFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.AdUnitCodeInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.BundleFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.BundleInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.ChannelFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DataCenterFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DataCenterInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DeviceCountryFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DeviceCountryInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DeviceTypeFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DeviceTypeInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.DomainFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.EidAvailableFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.EidInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.FpdAvailableFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.GppSidAvailableFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.GppSidInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.MediaTypeInFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.PrebidKeyFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.TcfInScopeFunction;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.UserFpdAvailableFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.StageSpecification;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.InvalidSchemaFunctionException;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RequestSpecification implements StageSpecification<RequestContext, BidRequest, AuctionContext> {

    public static final Set<String> PER_IMP_SCHEMA_FUNCTIONS =
            Set.of(AdUnitCodeFunction.NAME, MediaTypeInFunction.NAME);

    private final Map<String, SchemaFunction<RequestContext>> schemaFunctions;
    private final Map<String, ResultFunction<BidRequest, AuctionContext>> resultFunctions;

    public RequestSpecification(ObjectMapper mapper,
                                BidderCatalog bidderCatalog) {

        schemaFunctions = new HashMap<>();
        schemaFunctions.put(AdUnitCodeFunction.NAME, new AdUnitCodeFunction());
        schemaFunctions.put(AdUnitCodeInFunction.NAME, new AdUnitCodeInFunction());
        schemaFunctions.put(BundleFunction.NAME, new BundleFunction());
        schemaFunctions.put(BundleInFunction.NAME, new BundleInFunction());
        schemaFunctions.put(ChannelFunction.NAME, new ChannelFunction());
        schemaFunctions.put(DataCenterFunction.NAME, new DataCenterFunction());
        schemaFunctions.put(DataCenterInFunction.NAME, new DataCenterInFunction());
        schemaFunctions.put(DeviceCountryFunction.NAME, new DeviceCountryFunction());
        schemaFunctions.put(DeviceCountryInFunction.NAME, new DeviceCountryInFunction());
        schemaFunctions.put(DeviceTypeFunction.NAME, new DeviceTypeFunction());
        schemaFunctions.put(DeviceTypeInFunction.NAME, new DeviceTypeInFunction());
        schemaFunctions.put(DomainFunction.NAME, new DomainFunction());
        schemaFunctions.put(EidAvailableFunction.NAME, new EidAvailableFunction());
        schemaFunctions.put(EidInFunction.NAME, new EidInFunction());
        schemaFunctions.put(FpdAvailableFunction.NAME, new FpdAvailableFunction());
        schemaFunctions.put(GppSidAvailableFunction.NAME, new GppSidAvailableFunction());
        schemaFunctions.put(GppSidInFunction.NAME, new GppSidInFunction());
        schemaFunctions.put(MediaTypeInFunction.NAME, new MediaTypeInFunction());
        schemaFunctions.put(PrebidKeyFunction.NAME, new PrebidKeyFunction());
        schemaFunctions.put(TcfInScopeFunction.NAME, new TcfInScopeFunction());
        schemaFunctions.put(UserFpdAvailableFunction.NAME, new UserFpdAvailableFunction());

        resultFunctions = Map.of(
                ExcludeBiddersFunction.NAME, new ExcludeBiddersFunction(mapper, bidderCatalog),
                IncludeBiddersFunction.NAME, new IncludeBiddersFunction(mapper, bidderCatalog));
    }


    public SchemaFunction<RequestContext> schemaFunctionByName(String name) {
        final SchemaFunction<RequestContext> function = schemaFunctions.get(name);
        if (function == null) {
            throw new InvalidSchemaFunctionException(name);
        }

        return function;
    }

    public ResultFunction<BidRequest, AuctionContext> resultFunctionByName(String name) {
        final ResultFunction<BidRequest, AuctionContext> function = resultFunctions.get(name);
        if (function == null) {
            throw new InvalidSchemaFunctionException(name);
        }

        return function;
    }
}
