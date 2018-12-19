package org.prebid.server.bidder.grid;

import org.prebid.server.bidder.OpenrtbBidder;

public class GridBidder extends OpenrtbBidder {

    public GridBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, Void.class);
    }
}
