package org.prebid.server.bidder.visx;

import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.proto.openrtb.ext.request.visx.ExtImpVisx;

public class VisxBidder extends OpenrtbBidder<ExtImpVisx> {
    public VisxBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpVisx.class);
    }
}
