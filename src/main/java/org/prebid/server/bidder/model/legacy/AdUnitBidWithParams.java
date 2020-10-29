package org.prebid.server.bidder.model.legacy;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.auction.legacy.model.AdUnitBid;

@Deprecated
@AllArgsConstructor(staticName = "of")
@Value
public class AdUnitBidWithParams<T> {

    AdUnitBid adUnitBid;

    T params;
}
