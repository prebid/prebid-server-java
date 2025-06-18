package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.IteratorUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;

import java.util.HashSet;
import java.util.Set;

public class IncludeBiddersFunction extends FilterBiddersFunction {

    public static final String NAME = "includeBidders";

    public IncludeBiddersFunction(ObjectMapper objectMapper, BidderCatalog bidderCatalog) {
        super(objectMapper, bidderCatalog);
    }

    @Override
    protected FilterBiddersResult filterBidders(Imp imp,
                                                Set<String> bidders,
                                                Boolean ifSyncedId,
                                                UidsCookie uidsCookie) {

        final Set<String> removedBidders = new HashSet<>();
        final ObjectNode ext = imp.getExt();
        final ObjectNode updatedExt = ext.deepCopy();

        for (String bidder : IteratorUtils.asIterable(ext.fieldNames())) {
            if (!bidders.contains(bidder)) {
                updatedExt.remove(bidder);
                removedBidders.add(bidder);
            }
        }

        final Imp updatedImp = removedBidders.isEmpty() ? imp : imp.toBuilder().ext(updatedExt).build();
        return FilterBiddersResult.of(updatedImp, removedBidders);
    }
}
