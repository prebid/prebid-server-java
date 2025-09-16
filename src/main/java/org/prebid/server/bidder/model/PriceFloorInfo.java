package org.prebid.server.bidder.model;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class PriceFloorInfo {

    BigDecimal floor;

    String currency;
}
