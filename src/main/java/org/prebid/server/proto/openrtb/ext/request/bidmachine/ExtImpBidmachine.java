package org.prebid.server.proto.openrtb.ext.request.bidmachine;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpBidmachine {

    String host;

    String path;

    String sellerId;
}
