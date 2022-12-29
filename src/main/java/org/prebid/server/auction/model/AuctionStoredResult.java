package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import lombok.experimental.Accessors;

@Value(staticConstructor = "of")
@Accessors(fluent = true)
public class AuctionStoredResult {

    boolean hasStoredBidRequest;

    BidRequest bidRequest;
}
