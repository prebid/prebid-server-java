package org.prebid.server.bidder.mnet;

import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.mnet.ExtImpMnet;

/**
 * Mnet {@link Bidder} implementation.
 */
public class MnetBidder extends OpenrtbBidder<ExtImpMnet> {

    public MnetBidder(String endpointUrl, JacksonMapper jacksonMapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpMnet.class, jacksonMapper);
    }
}
