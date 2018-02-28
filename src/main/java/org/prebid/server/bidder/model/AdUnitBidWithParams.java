package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.auction.model.AdUnitBid;

@AllArgsConstructor(staticName = "of")
@Value
public class AdUnitBidWithParams<T> {

    AdUnitBid adUnitBid;

    T params;
}
