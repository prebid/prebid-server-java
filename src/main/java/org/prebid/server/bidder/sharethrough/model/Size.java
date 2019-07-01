package org.prebid.server.bidder.sharethrough.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Size {
    int height;

    int width;
}
