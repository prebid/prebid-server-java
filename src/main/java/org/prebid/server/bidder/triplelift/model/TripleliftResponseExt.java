package org.prebid.server.bidder.triplelift.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class TripleliftResponseExt {

    TripleliftInnerExt tripleliftPb;
}
