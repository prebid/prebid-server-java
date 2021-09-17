package org.prebid.server.bidder.medianet;

import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;

/**
 * Medianet {@link Bidder} implementation.
 */
public class MedianetBidder extends OpenrtbBidder<Void> {

    public MedianetBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl,
                RequestCreationStrategy.SINGLE_REQUEST,
                Void.class,
                mapper);
    }
}
