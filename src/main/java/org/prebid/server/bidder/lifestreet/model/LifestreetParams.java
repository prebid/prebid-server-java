package org.prebid.server.bidder.lifestreet.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class LifestreetParams {

    String slotTag;
}
