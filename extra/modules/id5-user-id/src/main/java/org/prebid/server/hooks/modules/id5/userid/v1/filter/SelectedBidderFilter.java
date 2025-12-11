package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.prebid.server.hooks.modules.id5.userid.v1.config.ValuesFilter;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

public class SelectedBidderFilter implements InjectActionFilter {

    private final ValuesFilter<String> biddersFilter;

    public SelectedBidderFilter(ValuesFilter<String> biddersFilter) {
        this.biddersFilter = biddersFilter;
    }

    @Override
    public FilterResult shouldInvoke(BidderRequestPayload payload, BidderInvocationContext invocationContext) {
        final String bidder = invocationContext.bidder();
        return biddersFilter.isValueAllowed(bidder) ? FilterResult.accepted()
                : FilterResult.rejected("bidder " + bidder + " rejected by config");
    }
}
