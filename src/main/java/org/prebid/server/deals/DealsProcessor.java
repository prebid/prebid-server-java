package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.util.StreamUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DealsProcessor {

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";
    private static final String PG_DEALS_ONLY = "pgdealsonly";

    private final JacksonMapper mapper;

    public DealsProcessor(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Imp removePgDealsOnlyBiddersWithoutDeals(AuctionContext auctionContext,
                                                    Imp imp,
                                                    BidderAliases aliases) {

        final Set<String> pgDealsOnlyBiddersToRemove = getPgDealsOnlyBiddersToRemove(imp, aliases);
        if (CollectionUtils.isNotEmpty(pgDealsOnlyBiddersToRemove)) {
            final Imp resolvedImp = removeBidders(imp, pgDealsOnlyBiddersToRemove);
            logBidderExclusion(auctionContext, imp, pgDealsOnlyBiddersToRemove);

            if (!hasBidder(resolvedImp)) {
                return null;
            }
            return resolvedImp;
        }

        return imp;
    }

    private Set<String> getPgDealsOnlyBiddersToRemove(Imp imp, BidderAliases aliases) {
        final Pmp pmp = imp.getPmp();
        final List<Deal> deals = pmp != null ? pmp.getDeals() : null;

        final Set<String> pgDealsOnlyBidders = findPgDealsOnlyBidders(imp);
        final Set<String> biddersWithDeals = CollectionUtils.emptyIfNull(deals).stream()
                .filter(Objects::nonNull)
                .map(Deal::getExt)
                .map(this::parseExt)
                .filter(Objects::nonNull)
                .map(ExtDeal::getLine)
                .map(ExtDealLine::getBidder)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(biddersWithDeals)) {
            return pgDealsOnlyBidders;
        }

        return pgDealsOnlyBidders.stream()
                .filter(bidder -> !biddersWithDeals.contains(bidder))
                .filter(bidder -> !biddersWithDeals.contains(aliases.resolveBidder(bidder)))
                .collect(Collectors.toSet());
    }

    private static Set<String> findPgDealsOnlyBidders(Imp imp) {
        return StreamUtil.asStream(bidderNodesFromImp(imp))
                .filter(bidderNode -> isPgDealsOnlyBidder(bidderNode.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private ExtDeal parseExt(ObjectNode node) {
        if (node == null) {
            return null;
        }
        try {
            return mapper.mapper().treeToValue(node, ExtDeal.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Iterator<Map.Entry<String, JsonNode>> bidderNodesFromImp(Imp imp) {
        return bidderParamsFromImpExt(imp.getExt()).fields();
    }

    private static JsonNode bidderParamsFromImpExt(ObjectNode ext) {
        return ext.path(PREBID_EXT).path(BIDDER_EXT);
    }

    private static boolean isPgDealsOnlyBidder(JsonNode bidder) {
        final JsonNode pgDealsOnlyNode = bidder.path(PG_DEALS_ONLY);
        return pgDealsOnlyNode.isBoolean() && pgDealsOnlyNode.asBoolean();
    }

    private static Imp removeBidders(Imp imp, Set<String> bidders) {
        final ObjectNode modifiedExt = imp.getExt().deepCopy();
        final ObjectNode extPrebidBidder = (ObjectNode) bidderParamsFromImpExt(modifiedExt);

        bidders.forEach(extPrebidBidder::remove);

        return imp.toBuilder().ext(modifiedExt).build();
    }

    private static void logBidderExclusion(AuctionContext auctionContext,
                                           Imp imp,
                                           Set<String> pgDealsOnlyBiddersToRemove) {

        auctionContext.getDebugWarnings().add(String.format(
                "Not calling %s bidders for impression %s due to %s flag and no available PG line items.",
                String.join(", ", pgDealsOnlyBiddersToRemove), imp.getId(), PG_DEALS_ONLY));
    }

    private static boolean hasBidder(Imp imp) {
        return bidderNodesFromImp(imp).hasNext();
    }
}
