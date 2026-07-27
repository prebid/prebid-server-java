package org.prebid.server.auction.bidderrequestpostprocessor;

import lombok.Value;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderError;

import java.util.Collections;
import java.util.List;

@Value(staticConstructor = "of")
public class BidderRequestPostProcessingResult {

    BidderRequest value;

    List<BidderError> errors;

    public static BidderRequestPostProcessingResult withValue(BidderRequest bidderRequest) {
        return BidderRequestPostProcessingResult.of(bidderRequest, Collections.emptyList());
    }
}
