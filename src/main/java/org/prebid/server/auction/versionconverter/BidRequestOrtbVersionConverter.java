package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;

import java.util.Objects;

public interface BidRequestOrtbVersionConverter {

    BidRequest convert(BidRequest bidRequest);

    default BidRequestOrtbVersionConverter andThen(BidRequestOrtbVersionConverter after) {
        Objects.requireNonNull(after);
        return bidRequest -> after.convert(this.convert(bidRequest));
    }

    static BidRequestOrtbVersionConverter identity() {
        return bidRequest -> bidRequest;
    }
}
