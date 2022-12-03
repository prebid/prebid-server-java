package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Getter;
import lombok.Value;

@Value(staticConstructor = "of")
@Getter
public class AuctionStoredResult {

    boolean hasStoredBidRequest;

    BidRequest bidRequest;
}
