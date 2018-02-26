package org.prebid.server.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.auction.model.AdUnitBid;

@AllArgsConstructor(staticName = "of")
@Value
public final class AdUnitBidWithParams<T> {

    AdUnitBid adUnitBid;

    T params;
}
