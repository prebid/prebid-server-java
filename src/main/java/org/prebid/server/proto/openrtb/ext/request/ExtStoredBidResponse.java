package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtStoredBidResponse {

    String bidder;

    String id;
}
