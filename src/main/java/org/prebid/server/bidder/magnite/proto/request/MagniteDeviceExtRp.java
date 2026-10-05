package org.prebid.server.bidder.magnite.proto.request;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class MagniteDeviceExtRp {

    BigDecimal pixelratio;
}
