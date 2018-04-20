package org.prebid.server.bidder.pulsepoint.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class NormalizedPulsepointParams {

    String publisherId;

    String tagId;

    Integer adSizeWidth;

    Integer adSizeHeight;
}
