package org.prebid.server.bidder.consumable.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class ConsumableBidResponse {

    Map<String, ConsumableDecision> decisions;
}
