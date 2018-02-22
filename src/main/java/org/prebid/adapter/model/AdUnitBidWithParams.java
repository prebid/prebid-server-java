package org.prebid.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.model.AdUnitBid;

@AllArgsConstructor(staticName = "of")
@Value
public final class AdUnitBidWithParams<T> {

    AdUnitBid adUnitBid;

    T params;
}
