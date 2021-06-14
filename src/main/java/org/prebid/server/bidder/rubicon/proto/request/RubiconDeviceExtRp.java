package org.prebid.server.bidder.rubicon.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconDeviceExtRp {

    BigDecimal pixelratio;
}
