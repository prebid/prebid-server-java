package org.prebid.server.bidder.lifestreet.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class LifestreetParams {

    String slotTag;
}
