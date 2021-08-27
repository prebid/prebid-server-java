package org.prebid.server.bidder.improvedigital.proto;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ImprovedigitalBidExtImprovedigital {

    String buyingType;

    Integer lineItemId;
}
