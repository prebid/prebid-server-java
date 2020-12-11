package org.prebid.server.proto.openrtb.ext.request.orbidder;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpOrbidder {

    String accountId;

    String placementId;

    BigDecimal bidfloor;
}
