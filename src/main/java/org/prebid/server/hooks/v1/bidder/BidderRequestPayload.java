package org.prebid.server.hooks.v1.bidder;

import com.iab.openrtb.request.BidRequest;

public interface BidderRequestPayload {

    BidRequest bidRequest();
}
