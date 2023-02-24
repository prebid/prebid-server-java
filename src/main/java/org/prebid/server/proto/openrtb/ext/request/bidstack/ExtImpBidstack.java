package org.prebid.server.proto.openrtb.ext.request.bidstack;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBidstack {
    String publisherId;
}
