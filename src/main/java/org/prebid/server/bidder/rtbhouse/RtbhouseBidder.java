package org.prebid.server.bidder.rtbhouse;

import org.prebid.server.bidder.OpenrtbBidder;

public class RtbhouseBidder extends OpenrtbBidder<Void> {
    public RtbhouseBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, Void.class);
    }
}
