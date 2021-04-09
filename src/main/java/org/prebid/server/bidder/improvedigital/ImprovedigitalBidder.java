package org.prebid.server.bidder.improvedigital;

import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;

/**
 * ImproveDigital {@link Bidder} implementation.
 */
public class ImprovedigitalBidder extends OpenrtbBidder<Void> {

    public ImprovedigitalBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, Void.class, mapper);
    }
}
