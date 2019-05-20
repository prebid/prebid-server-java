package org.prebid.server.bidder.consumable.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ConsumableContents {

    String body;
}
