package org.prebid.server.bidder.adnuntius.model.response;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class AdnuntiusNetBid {

    BigDecimal amount;
}
