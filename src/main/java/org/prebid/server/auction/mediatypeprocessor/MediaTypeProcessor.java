package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.BidRequest;

public interface MediaTypeProcessor {

    MediaTypeProcessingResult process(BidRequest bidRequest, String supportedBidderName);
}
