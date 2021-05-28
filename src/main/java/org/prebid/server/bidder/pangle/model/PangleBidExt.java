package org.prebid.server.bidder.pangle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@Value
public class PangleBidExt {

    BidExt pangle;
}
