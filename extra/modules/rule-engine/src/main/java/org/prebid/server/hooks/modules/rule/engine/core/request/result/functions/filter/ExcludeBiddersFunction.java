package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;

import java.util.HashSet;
import java.util.Set;

public class ExcludeBiddersFunction extends FilterBiddersFunction {

    public static final String NAME = "excludeBidders";

    public ExcludeBiddersFunction(ObjectMapper objectMapper, BidderCatalog bidderCatalog) {
        super(objectMapper, bidderCatalog);
    }

    @Override
    protected FilterBiddersResult filterBidders(Imp imp,
                                                Set<String> bidders,
                                                Boolean ifSyncedId,
                                                UidsCookie uidsCookie) {

        final Set<String> removedBidders = new HashSet<>();
        final ObjectNode updatedExt = imp.getExt().deepCopy();
        for (String bidder : bidders) {
            if (ifSyncedId != null && ifSyncedId != isBidderIdSynced(bidder, uidsCookie)) {
                continue;
            }

            updatedExt.remove(bidder);
            removedBidders.add(bidder);
        }

        final Imp updatedImp = removedBidders.isEmpty() ? imp : imp.toBuilder().ext(updatedExt).build();
        return FilterBiddersResult.of(updatedImp, removedBidders);
    }
}
