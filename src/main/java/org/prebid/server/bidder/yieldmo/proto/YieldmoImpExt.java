package org.prebid.server.bidder.yieldmo.proto;

import lombok.Value;

@Value(staticConstructor = "of")
public class YieldmoImpExt {

    String placementId;

    String gpid;
}
