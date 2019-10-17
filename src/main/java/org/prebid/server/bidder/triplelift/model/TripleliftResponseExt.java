package org.prebid.server.bidder.triplelift.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class TripleliftResponseExt {

    TripleliftInnerExt tripleliftPb;
}

