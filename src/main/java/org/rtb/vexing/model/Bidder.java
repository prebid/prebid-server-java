package org.rtb.vexing.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class Bidder {

    String bidderCode;

    List<AdUnitBid> adUnitBids;
}
