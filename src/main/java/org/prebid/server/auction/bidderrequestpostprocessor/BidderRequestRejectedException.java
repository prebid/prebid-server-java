package org.prebid.server.auction.bidderrequestpostprocessor;

import lombok.Getter;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.bidder.model.BidderError;

import java.util.List;
import java.util.Objects;

@Getter
public class BidderRequestRejectedException extends RuntimeException {

    private final BidRejectionReason rejectionReason;
    private final List<BidderError> errors;

    public BidderRequestRejectedException(BidRejectionReason bidRejectionReason, List<BidderError> errors) {
        this.rejectionReason = Objects.requireNonNull(bidRejectionReason);
        this.errors = Objects.requireNonNull(errors);
    }
}
