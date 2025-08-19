package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.prebid.server.hooks.v1.analytics.Tags;
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

            switch (filterBidders(imp, config.getBidders(), ifSyncedId, uidsCookie)) {
                case FilterBiddersResult.NoAction noAction -> updatedImps.add(imp);

                case FilterBiddersResult.Reject reject ->
                        seatNonBid.addAll(toSeatNonBid(imp.getId(), reject.bidders(), rejectionReason));

                case FilterBiddersResult.Update update -> {
                    updatedImps.add(update.imp());
                    seatNonBid.addAll(toSeatNonBid(imp.getId(), update.bidders(), rejectionReason));
                }
            }
        }

        final Tags tags = AnalyticsMapper.toTags(
                mapper, name(), seatNonBid, infrastructureArguments, config.getAnalyticsValue());

        if (updatedImps.isEmpty()) {
            return RuleResult.rejected(tags, seatNonBid);
        }

        final RuleAction action = !seatNonBid.isEmpty() ? RuleAction.UPDATE : RuleAction.NO_ACTION;
        final BidRequest result = action == RuleAction.UPDATE
                ? bidRequest.toBuilder().imp(updatedImps).build()
                : bidRequest;
        
        return RuleResult.of(result, action, tags, seatNonBid);
    }

    private static List<SeatNonBid> toSeatNonBid(String impId, Set<String> bidders, BidRejectionReason reason) {
        return bidders.stream()
                .map(bidder -> SeatNonBid.of(bidder, Collections.singletonList(NonBid.of(impId, reason))))
                .toList();
    }

    private FilterBiddersResult filterBidders(Imp imp,
                                              Set<String> bidders,
                                              Boolean ifSyncedId,
                                              UidsCookie uidsCookie) {

        final Set<String> biddersToRemove = biddersToRemove(imp, ifSyncedId, bidders, uidsCookie);
        if (biddersToRemove.isEmpty()) {
            return FilterBiddersResult.NoAction.instance();
        }

        final ObjectNode updatedExt = imp.getExt().deepCopy();
        final ObjectNode updatedBiddersNode = FilterUtils.bidderNode(updatedExt);
        biddersToRemove.forEach(updatedBiddersNode::remove);

        return updatedBiddersNode.isEmpty()
                ? new FilterBiddersResult.Reject(biddersToRemove)
                : new FilterBiddersResult.Update(imp.toBuilder().ext(updatedExt).build(), biddersToRemove);
    }

    protected abstract Set<String> biddersToRemove(Imp imp,
                                                   Boolean ifSyncedId,
                                                   Set<String> bidders,
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

    private FilterBiddersFunctionConfig parseConfig(ObjectNode config) {
        try {
            return mapper.treeToValue(config, FilterBiddersFunctionConfig.class);
        } catch (JsonProcessingException e) {
            throw new ConfigurationValidationException(e.getMessage());
        }
    }

    protected abstract String name();
}
