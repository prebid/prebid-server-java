package org.prebid.server.bidder.rtbhouse;

import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;

public class RtbhouseBidder extends OpenrtbBidder<Void> {

    public RtbhouseBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, Void.class, mapper);
    }
}
