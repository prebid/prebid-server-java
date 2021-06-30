package org.prebid.server.proto.openrtb.ext.request.bidscube;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpBidscube {

    String placementId;
}
