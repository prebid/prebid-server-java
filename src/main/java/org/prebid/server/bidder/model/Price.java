package org.prebid.server.bidder.model;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class Price {

    private static final Price EMPTY = Price.of(null, null);

    String currency;

    BigDecimal value;

    public static Price empty() {
        return Price.EMPTY;
    }
}
