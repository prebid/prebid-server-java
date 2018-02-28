package org.prebid.server.bidder.appnexus.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AppnexusImpExtAppnexus {

    Integer placementId;

    String keywords;

    String trafficSourceCode;
}
