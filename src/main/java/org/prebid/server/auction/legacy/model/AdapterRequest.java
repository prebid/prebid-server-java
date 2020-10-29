package org.prebid.server.auction.legacy.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Deprecated
@AllArgsConstructor(staticName = "of")
@Value
public class AdapterRequest {

    String bidderCode;

    List<AdUnitBid> adUnitBids;
}
