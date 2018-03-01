package org.prebid.server.bidder.pubmatic.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class NormalizedPubmaticParams {

    String publisherId;

    String adSlot;

    String tagId;

    Integer width;

    Integer height;
}
