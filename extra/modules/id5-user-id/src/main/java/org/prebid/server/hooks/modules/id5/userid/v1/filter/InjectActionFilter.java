package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

public interface InjectActionFilter {

    FilterResult shouldInvoke(BidderRequestPayload payload,
                              BidderInvocationContext invocationContext);
}
