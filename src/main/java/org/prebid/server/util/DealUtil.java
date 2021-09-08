package org.prebid.server.util;

import org.prebid.server.auction.BidderAliases;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;

import java.util.Objects;

public class DealUtil {

    private DealUtil() {
    }

    /**
     * Returns true if imp[].pmp.deal[].ext.line object has given bidder.
     */
    public static boolean isBidderHasDeal(String bidder, ExtDeal extDeal, BidderAliases aliases) {
        final ExtDealLine extDealLine = extDeal != null ? extDeal.getLine() : null;
        final String dealLineBidder = extDealLine != null ? extDealLine.getBidder() : null;
        return dealLineBidder == null || Objects.equals(dealLineBidder, bidder)
                || Objects.equals(aliases.resolveBidder(dealLineBidder), bidder)
                || Objects.equals(aliases.resolveBidder(bidder), dealLineBidder); // filter only PG related deals
    }
}
