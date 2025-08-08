package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class FilterBiddersFunction implements ResultFunction<BidRequest, RequestRuleContext> {

    private final ObjectMapper mapper;
    protected final BidderCatalog bidderCatalog;

    public FilterBiddersFunction(ObjectMapper mapper, BidderCatalog bidderCatalog) {
        this.mapper = Objects.requireNonNull(mapper);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public RuleResult<BidRequest> apply(ResultFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        final FilterBiddersFunctionConfig config = parseConfig(arguments.getConfig());

        final BidRequest bidRequest = arguments.getOperand();
        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                arguments.getInfrastructureArguments();

        final UidsCookie uidsCookie = infrastructureArguments.getContext().getAuctionContext().getUidsCookie();
        final Boolean ifSyncedId = config.getIfSyncedId();
        final BidRejectionReason rejectionReason = config.getSeatNonBid();
        final Granularity granularity = infrastructureArguments.getContext().getGranularity();

        final List<Imp> updatedImps = new ArrayList<>();
        final List<SeatNonBid> seatNonBid = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            if (granularity instanceof Granularity.Imp &&
                    !StringUtils.equals(((Granularity.Imp) granularity).impId(), imp.getId())) {

                updatedImps.add(imp);
                continue;
            }

            final FilterBiddersResult result = filterBidders(imp, config.getBidders(), ifSyncedId, uidsCookie);
            updatedImps.add(result.getImp());
            seatNonBid.addAll(toSeatNonBid(result, rejectionReason));
        }

        final UpdateResult<BidRequest> updateResult = !seatNonBid.isEmpty()
                ? UpdateResult.updated(bidRequest.toBuilder().imp(updatedImps).build())
                : UpdateResult.unaltered(bidRequest);

        final Tags tags = AnalyticsMapper.toTags(
                mapper, name(), seatNonBid, infrastructureArguments, config.getAnalyticsValue());

        return RuleResult.of(updateResult, tags, seatNonBid);
    }

    private static List<SeatNonBid> toSeatNonBid(FilterBiddersResult filterBiddersResult, BidRejectionReason reason) {
        final String impId = filterBiddersResult.getImp().getId();
        return filterBiddersResult.getBidders().stream()
                .map(bidder -> SeatNonBid.of(bidder, Collections.singletonList(NonBid.of(impId, reason))))
                .toList();
    }

    private FilterBiddersResult filterBidders(Imp imp,
                                              Set<String> bidders,
                                              Boolean ifSyncedId,
                                              UidsCookie uidsCookie) {

        final ObjectNode impExt = imp.getExt();
        final Set<String> removedBidders = biddersToRemove(imp, bidders, ifSyncedId, uidsCookie);

        if (removedBidders.isEmpty()) {
            return FilterBiddersResult.of(imp, removedBidders);
        }

        final ObjectNode updatedExt = impExt.deepCopy();
        final ObjectNode updatedBiddersNode = FilterUtils.bidderNode(updatedExt);
        removedBidders.forEach(updatedBiddersNode::remove);

        final Imp updatedImp = removedBidders.isEmpty() ? imp : imp.toBuilder().ext(updatedExt).build();
        return FilterBiddersResult.of(updatedImp, removedBidders);
    }

    protected abstract Set<String> biddersToRemove(Imp imp,
                                                   Set<String> bidders,
                                                   Boolean ifSyncedId,
                                                   UidsCookie uidsCookie);

    protected boolean isBidderIdSynced(String bidder, UidsCookie uidsCookie) {
        return bidderCatalog.cookieFamilyName(bidder)
                .map(uidsCookie::hasLiveUidFrom)
                .orElse(false);
    }

    @Override
    public void validateConfig(ObjectNode config) {
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

    protected abstract String name();
}
