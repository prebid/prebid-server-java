package org.prebid.server.bidder.mediasquare.request;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class MediasquareFloor {

    BigDecimal floor;

    String currency;
}
