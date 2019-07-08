package org.prebid.server.bidder.sharethrough.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Size {

    Integer height;

    Integer width;
}

