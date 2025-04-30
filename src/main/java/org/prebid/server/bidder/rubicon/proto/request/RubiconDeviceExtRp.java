package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class RubiconDeviceExtRp {

    BigDecimal pixelratio;
}
