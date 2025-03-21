package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CompositeMediaTypeProcessor implements MediaTypeProcessor {

    private final List<MediaTypeProcessor> mediaTypeProcessors;

    public CompositeMediaTypeProcessor(List<MediaTypeProcessor> mediaTypeProcessors) {
        this.mediaTypeProcessors = Objects.requireNonNull(mediaTypeProcessors);
    }

    @Override
    public MediaTypeProcessingResult process(BidRequest originalBidRequest,
                                             String bidderName,
                                             BidderAliases aliases,
                                             Account account) {
        BidRequest bidRequest = originalBidRequest;
        final List<BidderError> errors = new ArrayList<>();

        for (MediaTypeProcessor mediaTypeProcessor : mediaTypeProcessors) {
            final MediaTypeProcessingResult result = mediaTypeProcessor.process(
                    bidRequest,
                    bidderName,
                    aliases,
                    account);

            bidRequest = result.getBidRequest();
            errors.addAll(result.getErrors());

            if (result.isRejected()) {
                return MediaTypeProcessingResult.rejected(errors);
            }
        }

        return MediaTypeProcessingResult.succeeded(bidRequest, errors);
    }
}
