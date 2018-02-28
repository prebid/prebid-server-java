package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class AdapterRequest {

    String bidderCode;

    List<AdUnitBid> adUnitBids;
}
