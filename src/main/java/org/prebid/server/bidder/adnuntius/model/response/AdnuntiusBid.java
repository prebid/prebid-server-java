package org.prebid.server.bidder.adnuntius.model.response;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class AdnuntiusBid {

    BigDecimal amount;

    String currency;
}
