package org.prebid.server.bidder.medianet;

import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.medianet.ExtImpMedianet;

/**
 * Medianet {@link Bidder} implementation.
 */
public class MedianetBidder extends OpenrtbBidder<ExtImpMedianet> {

    public MedianetBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpMedianet.class, mapper);
    }
}
