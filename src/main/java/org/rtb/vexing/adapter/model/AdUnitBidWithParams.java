package org.rtb.vexing.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.rtb.vexing.model.AdUnitBid;

@AllArgsConstructor(staticName = "of")
@Value
public final class AdUnitBidWithParams<T> {

    AdUnitBid adUnitBid;

    T params;
}
