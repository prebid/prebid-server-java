package org.prebid.server.proto.openrtb.ext.request.orbidder;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpOrbidder {

    String accountId;

    String placementId;

    BigDecimal bidfloor;
}
