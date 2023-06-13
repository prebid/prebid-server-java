package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.bidder.model.BidderError;

import java.util.List;

@Value(staticConstructor = "of")
public class MediaTypeProcessingResult {

    BidRequest bidRequest;

    List<BidderError> errors;

    boolean rejected;

    public static MediaTypeProcessingResult succeeded(BidRequest bidRequest, List<BidderError> errors) {
        return MediaTypeProcessingResult.of(bidRequest, errors, false);
    }

    public static MediaTypeProcessingResult rejected(List<BidderError> errors) {
        return MediaTypeProcessingResult.of(null, errors, true);
    }
}
