package org.prebid.server.proto.openrtb.ext.request.bidmachine;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBidmachine {

    String host;

    String path;

    String sellerId;
}
