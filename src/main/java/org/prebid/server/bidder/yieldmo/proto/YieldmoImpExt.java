package org.prebid.server.bidder.yieldmo.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class YieldmoImpExt {

    String placementId;
}
