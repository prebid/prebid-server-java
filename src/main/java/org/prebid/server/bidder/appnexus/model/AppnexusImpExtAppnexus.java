package org.prebid.server.bidder.appnexus.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class AppnexusImpExtAppnexus {

    Integer placementId;

    String keywords;

    String trafficSourceCode;
}
