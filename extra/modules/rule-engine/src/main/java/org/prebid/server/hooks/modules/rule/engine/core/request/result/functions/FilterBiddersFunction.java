package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.AdUnitCodeFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.model.UpdateResult;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class FilterBiddersFunction implements ResultFunction<BidRequest, AuctionContext> {

    private final ObjectMapper mapper;
    private final BidderCatalog bidderCatalog;

    public FilterBiddersFunction(ObjectMapper mapper, BidderCatalog bidderCatalog) {
        this.mapper = Objects.requireNonNull(mapper);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public RuleResult<BidRequest> apply(ResultFunctionArguments<BidRequest, AuctionContext> arguments) {
        final FilterBiddersFunctionConfig config = parseConfig(arguments.getConfig());

        final BidRequest bidRequest = arguments.getOperand();
        final InfrastructureArguments<AuctionContext> infrastructureArguments = arguments.getInfrastructureArguments();
        final UidsCookie uidsCookie = infrastructureArguments.getContext().getUidsCookie();
        final Boolean ifSyncedId = config.getIfSyncedId();

        boolean updated = false;
        final List<Activity> activities = new ArrayList<>();
        final List<Imp> updatedImps = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            if (shouldSkipAdUnit(imp, infrastructureArguments)) {
                updatedImps.add(imp);
                continue;
            }

            final UpdateResult<Imp> impUpdateResult = filterBidders(imp, config.getBidders(), ifSyncedId, uidsCookie);
            updatedImps.add(impUpdateResult.getValue());
            updated = updated || impUpdateResult.isUpdated();
        }

        final UpdateResult<BidRequest> updateResult = updated
                ? UpdateResult.updated(bidRequest.toBuilder().imp(updatedImps).build())
                : UpdateResult.unaltered(bidRequest);

        return RuleResult.of(updateResult, TagsImpl.of(activities));
    }

    private static boolean shouldSkipAdUnit(Imp imp, InfrastructureArguments<AuctionContext> arguments) {
        return imp.getExt() == null
                || (arguments.getSchemaFunctionResults().containsKey(AdUnitCodeFunction.NAME)
                && !arguments.getSchemaFunctionMatches().get(AdUnitCodeFunction.NAME).equals("*")
                && arguments.getSchemaFunctionMatches().get(AdUnitCodeFunction.NAME).equals(imp.getId()));
    }

    protected abstract UpdateResult<Imp> filterBidders(Imp imp,
                                                       List<String> bidders,
                                                       Boolean ifSyncedId,
                                                       UidsCookie uidsCookie);

    protected boolean isBidderIdSynced(String bidder, UidsCookie uidsCookie) {
        return bidderCatalog.cookieFamilyName(bidder)
                .map(uidsCookie::hasLiveUidFrom)
                .orElse(false);
    }

    @Override
    public void validateConfig(JsonNode config) {
        final FilterBiddersFunctionConfig parsedConfig = parseConfig(config);
        if (parsedConfig == null) {
            throw new ConfigurationValidationException("Configuration is required, but not provided");
        }

        if (CollectionUtils.isEmpty(parsedConfig.getBidders())) {
            throw new ConfigurationValidationException("'bidders' field is required");
        }
    }

    private FilterBiddersFunctionConfig parseConfig(JsonNode config) {
        try {
            return mapper.treeToValue(config, FilterBiddersFunctionConfig.class);
        } catch (JsonProcessingException e) {
            throw new ConfigurationValidationException(e.getMessage());
        }
    }
}
