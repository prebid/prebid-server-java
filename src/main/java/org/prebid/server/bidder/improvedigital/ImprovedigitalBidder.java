package org.prebid.server.bidder.improvedigital;

import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;

/**
 * ImproveDigital {@link Bidder} implementation.
 */
public class ImprovedigitalBidder extends OpenrtbBidder<Void> {
    public ImprovedigitalBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, Void.class);
    }
}
