package org.prebid.server.bidder;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class Price {

    String currency;

    BigDecimal price;
}
