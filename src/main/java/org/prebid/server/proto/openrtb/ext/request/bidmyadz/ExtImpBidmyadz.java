package org.prebid.server.proto.openrtb.ext.request.bidmyadz;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpBidmyadz {

    String placementId;
}
