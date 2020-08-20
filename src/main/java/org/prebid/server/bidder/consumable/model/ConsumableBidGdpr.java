package org.prebid.server.bidder.consumable.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ConsumableBidGdpr {

    Boolean applies;

    String consent;
}
