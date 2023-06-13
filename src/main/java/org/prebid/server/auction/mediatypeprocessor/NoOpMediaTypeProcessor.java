package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.BidRequest;

import java.util.Collections;

public class NoOpMediaTypeProcessor implements MediaTypeProcessor {

    @Override
    public MediaTypeProcessingResult process(BidRequest bidRequest, String supportedBidderName) {
        return MediaTypeProcessingResult.succeeded(bidRequest, Collections.emptyList());
    }
}
